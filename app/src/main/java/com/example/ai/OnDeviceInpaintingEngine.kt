package com.example.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.example.data.model.FillVariation
import com.example.data.model.QualityMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

data class InpaintOutcome(
    val resultBitmap: Bitmap,
    val areaTooLargeWarning: Boolean,
    val variations: List<FillVariation> = emptyList()
)

class OnDeviceInpaintingEngine(private val segmentationEngine: OnDeviceSegmentationEngine) {

    /**
     * Detects the main subject and optionally its cast shadow/reflection on the ground.
     */
    suspend fun detectSubjectForRemoval(
        source: Bitmap,
        includeShadowAndReflection: Boolean,
        quality: QualityMode,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): SegmentationResult = withContext(Dispatchers.Default) {
        val seg = segmentationEngine.detectSubjectMask(
            source = source,
            subjectType = com.example.data.model.SubjectType.AUTO,
            hairDetail = false,
            quality = quality,
            onProgress = onProgress
        )
        if (!seg.subjectFound || !includeShadowAndReflection) {
            return@withContext seg
        }

        val expanded = expandMaskWithShadowDetector(source, seg.mask)
        seg.mask.recycle()
        seg.copy(mask = expanded)
    }

    /**
     * Detects darkened cast shadow or soft reflection pixels near/below the subject mask.
     */
    private fun expandMaskWithShadowDetector(source: Bitmap, mask: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val sPx = IntArray(w * h)
        source.getPixels(sPx, 0, w, 0, 0, w, h)
        val mPx = IntArray(w * h)
        mask.getPixels(mPx, 0, w, 0, 0, w, h)

        var minX = w; var maxX = 0; var minY = h; var maxY = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (((mPx[y * w + x] ushr 24) and 0xFF) > 128) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX <= minX || maxY <= minY) return mask.copy(Bitmap.Config.ARGB_8888, true)

        val subjW = maxX - minX
        val subjH = maxY - minY
        // Estimate ambient background luminance from outside the subject band
        var bgLumSum = 0L
        var bgCount = 0
        val samplePad = max(12, subjW / 6)
        for (y in max(0, maxY - subjH / 3) until min(h, maxY + subjH / 3)) {
            for (x in max(0, minX - samplePad) until min(w, maxX + samplePad)) {
                val idx = y * w + x
                if (((mPx[idx] ushr 24) and 0xFF) < 30) {
                    val p = sPx[idx]
                    val lum = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                    bgLumSum += lum
                    bgCount++
                }
            }
        }
        val avgBgLum = if (bgCount > 0) (bgLumSum / bgCount).toInt() else 128

        val shadowBandY0 = max(0, maxY - (subjH * 0.18f).toInt())
        val shadowBandY1 = min(h - 1, maxY + (subjH * 0.25f).toInt())
        val shadowBandX0 = max(0, minX - (subjW * 0.22f).toInt())
        val shadowBandX1 = min(w - 1, maxX + (subjW * 0.22f).toInt())

        val outPx = mPx.copyOf()
        for (y in shadowBandY0..shadowBandY1) {
            for (x in shadowBandX0..shadowBandX1) {
                val idx = y * w + x
                if (((outPx[idx] ushr 24) and 0xFF) > 128) continue
                val p = sPx[idx]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                // Shadow cue: noticeably darker than local ambient background, low chroma saturation
                val maxC = maxOf(r, g, b)
                val minC = minOf(r, g, b)
                val sat = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f
                if (lum < avgBgLum * 0.78f && sat < 0.35f) {
                    outPx[idx] = 0xFFFFFFFF.toInt()
                }
            }
        }
        return Bitmap.createBitmap(outPx, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Grows or shrinks a binary selection mask by growPx pixels ("Mask thoda bada karo").
     */
    fun growSelectionMask(mask: Bitmap, growPx: Int): Bitmap {
        if (growPx <= 0) return mask.copy(Bitmap.Config.ARGB_8888, true)
        val w = mask.width
        val h = mask.height
        val px = IntArray(w * h)
        mask.getPixels(px, 0, w, 0, 0, w, h)
        var binary = BooleanArray(w * h) { ((px[it] ushr 24) and 0xFF) > 96 }
        repeat(growPx.coerceAtMost(28)) {
            val next = binary.copyOf()
            for (y in 1 until h - 1) {
                val row = y * w
                for (x in 1 until w - 1) {
                    val i = row + x
                    if (!binary[i]) {
                        if (binary[i - 1] || binary[i + 1] || binary[i - w] || binary[i + w]) {
                            next[i] = true
                        }
                    }
                }
            }
            binary = next
        }
        val out = IntArray(w * h) { if (binary[it]) 0xFFFFFFFF.toInt() else 0 }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    fun paintSelectionBrush(
        mask: Bitmap,
        normX: Float,
        normY: Float,
        radiusNorm: Float,
        softness: Float,
        addMode: Boolean
    ) {
        val w = mask.width
        val h = mask.height
        val cx = (normX * w).roundToInt()
        val cy = (normY * h).roundToInt()
        val rad = (radiusNorm * min(w, h)).roundToInt().coerceAtLeast(3)
        val x0 = (cx - rad).coerceAtLeast(0)
        val y0 = (cy - rad).coerceAtLeast(0)
        val x1 = (cx + rad).coerceAtMost(w - 1)
        val y1 = (cy + rad).coerceAtMost(h - 1)
        val bw = x1 - x0 + 1
        val bh = y1 - y0 + 1
        if (bw <= 0 || bh <= 0) return
        val px = IntArray(bw * bh)
        mask.getPixels(px, 0, bw, x0, y0, bw, bh)
        val target = if (addMode) 255 else 0
        for (y in y0..y1) {
            for (x in x0..x1) {
                val d = hypot((x - cx).toFloat(), (y - cy).toFloat()) / rad
                if (d > 1f) continue
                val hardEdge = (1f - softness).coerceIn(0.1f, 0.98f)
                val alpha = if (d <= hardEdge) 1f else 1f - ((d - hardEdge) / (1f - hardEdge))
                val idx = (y - y0) * bw + (x - x0)
                val old = (px[idx] ushr 24) and 0xFF
                val next = (old * (1f - alpha) + target * alpha).roundToInt().coerceIn(0, 255)
                px[idx] = (next shl 24) or (next shl 16) or (next shl 8) or next
            }
        }
        mask.setPixels(px, 0, bw, x0, y0, bw, bh)
    }

    suspend fun inpaintWithVariations(
        source: Bitmap,
        selectionMask: Bitmap,
        quality: QualityMode,
        generateVariations: Boolean,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): InpaintOutcome = withContext(Dispatchers.Default) {
        val w = source.width
        val h = source.height
        val scaledMask = if (selectionMask.width == w && selectionMask.height == h) selectionMask
        else Bitmap.createScaledBitmap(selectionMask, w, h, true)

        val mPx = IntArray(w * h)
        scaledMask.getPixels(mPx, 0, w, 0, 0, w, h)
        if (scaledMask !== selectionMask) scaledMask.recycle()

        // Find tight bounding box of hole
        var minX = w; var maxX = -1; var minY = h; var maxY = -1
        var holeCount = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (((mPx[y * w + x] ushr 24) and 0xFF) > 64) {
                    holeCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (holeCount == 0 || maxX < minX) {
            return@withContext InpaintOutcome(source.copy(Bitmap.Config.ARGB_8888, true), false)
        }

        val coverage = holeCount.toFloat() / (w * h)
        val tooLarge = coverage > 0.48f

        onProgress(0.2f, "Aas-paas ka texture samjha ja raha hai...")
        // Padded ROI around the hole for full-res localized processing without OOM
        val pad = max(48, max(maxX - minX, maxY - minY) / 2).coerceAtMost(240)
        val roiX0 = (minX - pad).coerceAtLeast(0)
        val roiY0 = (minY - pad).coerceAtLeast(0)
        val roiX1 = (maxX + pad).coerceAtMost(w - 1)
        val roiY1 = (maxY + pad).coerceAtMost(h - 1)
        val roiW = roiX1 - roiX0 + 1
        val roiH = roiY1 - roiY0 + 1

        val roiPixels = IntArray(roiW * roiH)
        source.getPixels(roiPixels, 0, roiW, roiX0, roiY0, roiW, roiH)
        val roiHole = BooleanArray(roiW * roiH)
        for (y in 0 until roiH) {
            for (x in 0 until roiW) {
                roiHole[y * roiW + x] = ((mPx[(roiY0 + y) * w + (roiX0 + x)] ushr 24) and 0xFF) > 64
            }
        }

        // Work at bounded ROI size for PatchMatch speed, then blend seamlessly back at full ROI res
        val maxRoiWork = if (quality == QualityMode.HIGH) 420 else 260
        val primaryFilledRoi = runPatchMatchRoi(roiPixels, roiHole, roiW, roiH, maxRoiWork, seed = 17, quality = quality) { p ->
            onProgress(0.2f + p * 0.65f, if (quality == QualityMode.HIGH) "High quality texture bhar raha hai..." else "Fast fill chal raha hai...")
        }

        val finalFull = blendRoiIntoFull(source, primaryFilledRoi, roiX0, roiY0, roiW, roiH, roiHole)

        val variations = mutableListOf<FillVariation>()
        if (generateVariations) {
            onProgress(0.88f, "Naye fill options ban rahe hain...")
            variations += FillVariation(0, "Smooth Natural", finalFull)
            val alt1 = runPatchMatchRoi(roiPixels, roiHole, roiW, roiH, min(maxRoiWork, 280), seed = 109, quality = QualityMode.FAST) {}
            variations += FillVariation(1, "Texture Match", blendRoiIntoFull(source, alt1, roiX0, roiY0, roiW, roiH, roiHole))
            val alt2 = runPatchMatchRoi(roiPixels, roiHole, roiW, roiH, min(maxRoiWork, 280), seed = 401, quality = QualityMode.FAST) {}
            variations += FillVariation(2, "Soft Blend", blendRoiIntoFull(source, alt2, roiX0, roiY0, roiW, roiH, roiHole))
        }

        onProgress(1f, "Fill poora hua!")
        InpaintOutcome(finalFull, tooLarge, variations)
    }

    private fun runPatchMatchRoi(
        roiPixels: IntArray,
        roiHole: BooleanArray,
        roiW: Int,
        roiH: Int,
        maxWorkDim: Int,
        seed: Int,
        quality: QualityMode,
        onSubProgress: (Float) -> Unit
    ): IntArray {
        val scale = min(1f, maxWorkDim.toFloat() / max(roiW, roiH))
        val ww = (roiW * scale).roundToInt().coerceAtLeast(24)
        val wh = (roiH * scale).roundToInt().coerceAtLeast(24)

        val workPx = IntArray(ww * wh)
        val workHole = BooleanArray(ww * wh)
        for (y in 0 until wh) {
            val sy = (y / scale).roundToInt().coerceIn(0, roiH - 1)
            for (x in 0 until ww) {
                val sx = (x / scale).roundToInt().coerceIn(0, roiW - 1)
                workPx[y * ww + x] = roiPixels[sy * roiW + sx]
                workHole[y * ww + x] = roiHole[sy * roiW + sx]
            }
        }

        // Step 1: Multi-pass inward onion-peel harmonic diffusion initialization
        onSubProgress(0.25f)
        inwardHarmonicInit(workPx, workHole, ww, wh)

        // Step 2: PatchMatch Nearest-Neighbor texture synthesis for sharp natural detail
        onSubProgress(0.55f)
        val iterations = if (quality == QualityMode.HIGH) 4 else 2
        patchMatchSynthesize(workPx, workHole, ww, wh, patchHalf = 2, iterations = iterations, seed = seed)
        onSubProgress(0.95f)

        if (ww == roiW && wh == roiH) return workPx

        // Upsample filled workPx back to full ROI size
        val out = roiPixels.copyOf()
        for (y in 0 until roiH) {
            val fy = (y * scale).coerceIn(0f, (wh - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = (y0 + 1).coerceAtMost(wh - 1)
            val wy = fy - y0
            for (x in 0 until roiW) {
                val i = y * roiW + x
                if (!roiHole[i]) continue
                val fx = (x * scale).coerceIn(0f, (ww - 1).toFloat())
                val x0 = fx.toInt()
                val x1 = (x0 + 1).coerceAtMost(ww - 1)
                val wx = fx - x0
                out[i] = bilerpColor(
                    workPx[y0 * ww + x0],
                    workPx[y0 * ww + x1],
                    workPx[y1 * ww + x0],
                    workPx[y1 * ww + x1],
                    wx,
                    wy
                )
            }
        }
        return out
    }

    private fun inwardHarmonicInit(px: IntArray, hole: BooleanArray, w: Int, h: Int) {
        val filled = BooleanArray(w * h) { !hole[it] }
        val frontier = ArrayList<Int>(w * h / 4)
        var remaining = filled.count { !it }
        var guard = 0
        while (remaining > 0 && guard < w + h) {
            guard++
            frontier.clear()
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val i = row + x
                    if (filled[i]) continue
                    if ((x > 0 && filled[i - 1]) ||
                        (x < w - 1 && filled[i + 1]) ||
                        (y > 0 && filled[i - w]) ||
                        (y < h - 1 && filled[i + w])
                    ) {
                        frontier.add(i)
                    }
                }
            }
            if (frontier.isEmpty()) break
            for (i in frontier) {
                val x = i % w
                val y = i / w
                var sr = 0; var sg = 0; var sb = 0; var cnt = 0
                for (dy in -2..2) {
                    val ny = y + dy
                    if (ny !in 0 until h) continue
                    for (dx in -2..2) {
                        val nx = x + dx
                        if (nx !in 0 until w) continue
                        val ni = ny * w + nx
                        if (filled[ni]) {
                            val c = px[ni]
                            sr += (c shr 16) and 0xFF
                            sg += (c shr 8) and 0xFF
                            sb += c and 0xFF
                            cnt++
                        }
                    }
                }
                if (cnt > 0) {
                    px[i] = (0xFF shl 24) or ((sr / cnt) shl 16) or ((sg / cnt) shl 8) or (sb / cnt)
                }
                filled[i] = true
                remaining--
            }
        }
        // Smooth initial harmonic fill inside hole
        repeat(4) {
            for (y in 1 until h - 1) {
                val row = y * w
                for (x in 1 until w - 1) {
                    val i = row + x
                    if (!hole[i]) continue
                    val c1 = px[i - 1]; val c2 = px[i + 1]; val c3 = px[i - w]; val c4 = px[i + w]
                    val r = (((c1 shr 16) and 0xFF) + ((c2 shr 16) and 0xFF) + ((c3 shr 16) and 0xFF) + ((c4 shr 16) and 0xFF)) / 4
                    val g = (((c1 shr 8) and 0xFF) + ((c2 shr 8) and 0xFF) + ((c3 shr 8) and 0xFF) + ((c4 shr 8) and 0xFF)) / 4
                    val b = ((c1 and 0xFF) + (c2 and 0xFF) + (c3 and 0xFF) + (c4 and 0xFF)) / 4
                    px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
    }

    private fun patchMatchSynthesize(
        px: IntArray,
        hole: BooleanArray,
        w: Int,
        h: Int,
        patchHalf: Int,
        iterations: Int,
        seed: Int
    ) {
        val rng = Random(seed)
        // Collect valid source pixel centers (completely outside hole)
        val validCoords = IntArray(w * h)
        var validCount = 0
        for (y in patchHalf until h - patchHalf) {
            for (x in patchHalf until w - patchHalf) {
                var clean = true
                for (dy in -patchHalf..patchHalf) {
                    for (dx in -patchHalf..patchHalf) {
                        if (hole[(y + dy) * w + (x + dx)]) {
                            clean = false
                            break
                        }
                    }
                    if (!clean) break
                }
                if (clean) {
                    validCoords[validCount++] = (y shl 16) or x
                }
            }
        }
        if (validCount < 8) return

        val nnfX = IntArray(w * h)
        val nnfY = IntArray(w * h)
        val nnfDist = IntArray(w * h) { Int.MAX_VALUE }

        for (y in patchHalf until h - patchHalf) {
            for (x in patchHalf until w - patchHalf) {
                val i = y * w + x
                if (!hole[i]) continue
                val pick = validCoords[rng.nextInt(validCount)]
                val sx = pick and 0xFFFF
                val sy = pick ushr 16
                nnfX[i] = sx
                nnfY[i] = sy
                nnfDist[i] = patchDistance(px, w, x, y, sx, sy, patchHalf)
            }
        }

        val maxSearch = max(w, h) / 2
        for (iter in 0 until iterations) {
            val reverse = iter % 2 == 1
            val yRange = if (reverse) (h - patchHalf - 1) downTo patchHalf else patchHalf until (h - patchHalf)
            val xRange = if (reverse) (w - patchHalf - 1) downTo patchHalf else patchHalf until (w - patchHalf)
            val step = if (reverse) 1 else -1

            for (y in yRange) {
                for (x in xRange) {
                    val i = y * w + x
                    if (!hole[i]) continue

                    // Propagation from neighbor along scanline
                    val nx = (x + step).coerceIn(patchHalf, w - patchHalf - 1)
                    val ni = y * w + nx
                    if (hole[ni]) {
                        val candX = (nnfX[ni] - step).coerceIn(patchHalf, w - patchHalf - 1)
                        val candY = nnfY[ni]
                        if (isValidPatch(hole, w, candX, candY, patchHalf)) {
                            val d = patchDistance(px, w, x, y, candX, candY, patchHalf)
                            if (d < nnfDist[i]) {
                                nnfX[i] = candX; nnfY[i] = candY; nnfDist[i] = d
                            }
                        }
                    }

                    val ny = (y + step).coerceIn(patchHalf, h - patchHalf - 1)
                    val mi = ny * w + x
                    if (hole[mi]) {
                        val candX = nnfX[mi]
                        val candY = (nnfY[mi] - step).coerceIn(patchHalf, h - patchHalf - 1)
                        if (isValidPatch(hole, w, candX, candY, patchHalf)) {
                            val d = patchDistance(px, w, x, y, candX, candY, patchHalf)
                            if (d < nnfDist[i]) {
                                nnfX[i] = candX; nnfY[i] = candY; nnfDist[i] = d
                            }
                        }
                    }

                    // Random search with exponentially decreasing radius
                    var rad = maxSearch
                    while (rad >= 2) {
                        val rx = (nnfX[i] + rng.nextInt(-rad, rad + 1)).coerceIn(patchHalf, w - patchHalf - 1)
                        val ry = (nnfY[i] + rng.nextInt(-rad, rad + 1)).coerceIn(patchHalf, h - patchHalf - 1)
                        if (isValidPatch(hole, w, rx, ry, patchHalf)) {
                            val d = patchDistance(px, w, x, y, rx, ry, patchHalf)
                            if (d < nnfDist[i]) {
                                nnfX[i] = rx; nnfY[i] = ry; nnfDist[i] = d
                            }
                        }
                        rad /= 2
                    }
                }
            }

            // Reconstruct hole pixels from best matched source patches
            val nextPx = px.copyOf()
            for (y in patchHalf until h - patchHalf) {
                for (x in patchHalf until w - patchHalf) {
                    val i = y * w + x
                    if (!hole[i]) continue
                    val sx = nnfX[i]
                    val sy = nnfY[i]
                    val srcColor = px[sy * w + sx]
                    val curColor = px[i]
                    val blend = if (iter == iterations - 1) 0.72f else 0.58f
                    nextPx[i] = lerpColor(curColor, srcColor, blend)
                }
            }
            System.arraycopy(nextPx, 0, px, 0, px.size)
        }
    }

    private fun isValidPatch(hole: BooleanArray, w: Int, cx: Int, cy: Int, half: Int): Boolean {
        for (dy in -half..half) {
            val row = (cy + dy) * w
            for (dx in -half..half) {
                if (hole[row + cx + dx]) return false
            }
        }
        return true
    }

    private fun patchDistance(px: IntArray, w: Int, ax: Int, ay: Int, bx: Int, by: Int, half: Int): Int {
        var sum = 0
        for (dy in -half..half) {
            val ra = (ay + dy) * w + ax
            val rb = (by + dy) * w + bx
            for (dx in -half..half) {
                val ca = px[ra + dx]
                val cb = px[rb + dx]
                val dr = ((ca shr 16) and 0xFF) - ((cb shr 16) and 0xFF)
                val dg = ((ca shr 8) and 0xFF) - ((cb shr 8) and 0xFF)
                val db = (ca and 0xFF) - (cb and 0xFF)
                sum += dr * dr + dg * dg + db * db
            }
        }
        return sum
    }

    private fun blendRoiIntoFull(
        source: Bitmap,
        filledRoi: IntArray,
        roiX0: Int,
        roiY0: Int,
        roiW: Int,
        roiH: Int,
        roiHole: BooleanArray
    ): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val outRoi = IntArray(roiW * roiH)
        out.getPixels(outRoi, 0, roiW, roiX0, roiY0, roiW, roiH)

        // Compute soft feather weights right around the hole boundary (2px seam feather)
        for (y in 0 until roiH) {
            for (x in 0 until roiW) {
                val i = y * roiW + x
                if (roiHole[i]) {
                    outRoi[i] = filledRoi[i]
                } else {
                    // Check if adjacent to hole for smooth 1-2px transition
                    val near = (x > 0 && roiHole[i - 1]) ||
                        (x < roiW - 1 && roiHole[i + 1]) ||
                        (y > 0 && roiHole[i - roiW]) ||
                        (y < roiH - 1 && roiHole[i + roiW])
                    if (near) {
                        outRoi[i] = lerpColor(outRoi[i], filledRoi[i], 0.35f)
                    }
                }
            }
        }
        out.setPixels(outRoi, 0, roiW, roiX0, roiY0, roiW, roiH)
        return out
    }

    /**
     * Manual Repair Tool 1 & 2: Clone Stamp and Healing Brush.
     */
    fun applyManualRepairBrush(
        bitmap: Bitmap,
        srcNormX: Float,
        srcNormY: Float,
        dstNormX: Float,
        dstNormY: Float,
        radiusNorm: Float,
        softness: Float,
        healingMode: Boolean
    ) {
        val w = bitmap.width
        val h = bitmap.height
        val sxCenter = (srcNormX * w).roundToInt()
        val syCenter = (srcNormY * h).roundToInt()
        val dxCenter = (dstNormX * w).roundToInt()
        val dyCenter = (dstNormY * h).roundToInt()
        val rad = (radiusNorm * min(w, h)).roundToInt().coerceAtLeast(4)

        val x0 = (dxCenter - rad).coerceAtLeast(0)
        val y0 = (dyCenter - rad).coerceAtLeast(0)
        val x1 = (dxCenter + rad).coerceAtMost(w - 1)
        val y1 = (dyCenter + rad).coerceAtMost(h - 1)
        val bw = x1 - x0 + 1
        val bh = y1 - y0 + 1
        if (bw <= 0 || bh <= 0) return

        val dstPx = IntArray(bw * bh)
        bitmap.getPixels(dstPx, 0, bw, x0, y0, bw, bh)

        // Mean luminance/RGB offset for Healing Brush so cloned texture matches local lighting
        var avgSrcR = 0f; var avgSrcG = 0f; var avgSrcB = 0f
        var avgDstR = 0f; var avgDstG = 0f; var avgDstB = 0f
        if (healingMode) {
            var cnt = 0
            for (y in y0..y1 step 2) {
                for (x in x0..x1 step 2) {
                    val sx = (sxCenter + (x - dxCenter)).coerceIn(0, w - 1)
                    val sy = (syCenter + (y - dyCenter)).coerceIn(0, h - 1)
                    val sc = bitmap.getPixel(sx, sy)
                    val dc = dstPx[(y - y0) * bw + (x - x0)]
                    avgSrcR += (sc shr 16) and 0xFF
                    avgSrcG += (sc shr 8) and 0xFF
                    avgSrcB += sc and 0xFF
                    avgDstR += (dc shr 16) and 0xFF
                    avgDstG += (dc shr 8) and 0xFF
                    avgDstB += dc and 0xFF
                    cnt++
                }
            }
            if (cnt > 0) {
                avgSrcR /= cnt; avgSrcG /= cnt; avgSrcB /= cnt
                avgDstR /= cnt; avgDstG /= cnt; avgDstB /= cnt
            }
        }

        for (y in y0..y1) {
            for (x in x0..x1) {
                val dist = hypot((x - dxCenter).toFloat(), (y - dyCenter).toFloat()) / rad
                if (dist > 1f) continue
                val hard = (1f - softness).coerceIn(0.1f, 0.95f)
                val alpha = if (dist <= hard) 1f else 1f - ((dist - hard) / (1f - hard))
                val sx = (sxCenter + (x - dxCenter)).coerceIn(0, w - 1)
                val sy = (syCenter + (y - dyCenter)).coerceIn(0, h - 1)
                val sc = bitmap.getPixel(sx, sy)
                val idx = (y - y0) * bw + (x - x0)
                val dc = dstPx[idx]
                val sr = if (healingMode) (((sc shr 16) and 0xFF) + (avgDstR - avgSrcR)).roundToInt().coerceIn(0, 255) else (sc shr 16) and 0xFF
                val sg = if (healingMode) (((sc shr 8) and 0xFF) + (avgDstG - avgSrcG)).roundToInt().coerceIn(0, 255) else (sc shr 8) and 0xFF
                val sb = if (healingMode) ((sc and 0xFF) + (avgDstB - avgSrcB)).roundToInt().coerceIn(0, 255) else sc and 0xFF
                val target = (0xFF shl 24) or (sr shl 16) or (sg shl 8) or sb
                dstPx[idx] = lerpColor(dc, target, alpha * 0.92f)
            }
        }
        bitmap.setPixels(dstPx, 0, bw, x0, y0, bw, bh)
    }

    /**
     * Manual Repair Tool 3: Patch Tool — copies a nearby clean polygon area over a drawn patch polygon.
     */
    fun applyPatchTool(
        bitmap: Bitmap,
        polygonNorm: List<Pair<Float, Float>>,
        shiftNormX: Float,
        shiftNormY: Float
    ) {
        if (polygonNorm.size < 3) return
        val w = bitmap.width
        val h = bitmap.height
        val maskBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(maskBmp)
        val path = Path().apply {
            val f = polygonNorm.first()
            moveTo(f.first * w, f.second * h)
            for (i in 1 until polygonNorm.size) {
                lineTo(polygonNorm[i].first * w, polygonNorm[i].second * h)
            }
            close()
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })

        val mPx = IntArray(w * h)
        maskBmp.getPixels(mPx, 0, w, 0, 0, w, h)
        maskBmp.recycle()

        val dx = (shiftNormX * w).roundToInt()
        val dy = (shiftNormY * h).roundToInt()
        val orig = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val outPx = IntArray(w * h)
        bitmap.getPixels(outPx, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val a = ((mPx[i] ushr 24) and 0xFF) / 255f
                if (a > 0.05f) {
                    val sx = (x + dx).coerceIn(0, w - 1)
                    val sy = (y + dy).coerceIn(0, h - 1)
                    outPx[i] = lerpColor(outPx[i], orig.getPixel(sx, sy), a)
                }
            }
        }
        orig.recycle()
        bitmap.setPixels(outPx, 0, w, 0, 0, w, h)
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val inv = 1f - t
        val r = (((a shr 16) and 0xFF) * inv + ((b shr 16) and 0xFF) * t).roundToInt().coerceIn(0, 255)
        val g = (((a shr 8) and 0xFF) * inv + ((b shr 8) and 0xFF) * t).roundToInt().coerceIn(0, 255)
        val bl = ((a and 0xFF) * inv + (b and 0xFF) * t).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun bilerpColor(c00: Int, c10: Int, c01: Int, c11: Int, wx: Float, wy: Float): Int {
        val top = lerpColor(c00, c10, wx)
        val bot = lerpColor(c01, c11, wx)
        return lerpColor(top, bot, wy)
    }
}
