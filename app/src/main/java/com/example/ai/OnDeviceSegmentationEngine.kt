package com.example.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.example.data.model.EdgeSettings
import com.example.data.model.QualityMode
import com.example.data.model.SubjectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class SegmentationResult(
    val mask: Bitmap, // ARGB_8888 where alpha channel (and grayscale RGB) stores 0..255 mask
    val detectedLabelHinglish: String,
    val subjectFound: Boolean
)

class OnDeviceSegmentationEngine(private val modelPool: LiteRtModelPool) {

    suspend fun detectSubjectMask(
        source: Bitmap,
        subjectType: SubjectType,
        hairDetail: Boolean,
        quality: QualityMode,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): SegmentationResult = withContext(Dispatchers.Default) {
        onProgress(0.15f, "Photo analyze ho raha hai...")
        val w = source.width
        val h = source.height

        var personAlpha: FloatArray? = null
        var objectAlpha: FloatArray? = null
        var personRatio = 0f
        var objectRatio = 0f

        if (subjectType == SubjectType.PERSON || subjectType == SubjectType.AUTO) {
            onProgress(0.32f, "Person AI model chal raha hai...")
            val selfieInput = LiteRtModelPool.bitmapToFloatRgb01(source, 256, 256)
            val selfieOut = modelPool.runModel(LiteRtModelPool.MODEL_SELFIE, 256, 256, 3, selfieInput)
            val upscaled = bilinearUpscaleFloat(selfieOut, 256, 256, w, h)
            if (hairDetail) {
                onProgress(0.50f, "Baal / hair detail refine ho raha hai...")
                val hairInput = LiteRtModelPool.bitmapToFloatRgba01(source, 512, 512)
                val hairOut = modelPool.runModel(LiteRtModelPool.MODEL_HAIR, 512, 512, 4, hairInput)
                val hairProb = FloatArray(512 * 512)
                for (i in 0 until 512 * 512) {
                    val bgLogit = hairOut[i * 2]
                    val fgLogit = hairOut[i * 2 + 1]
                    val maxL = max(bgLogit, fgLogit)
                    val eBg = exp((bgLogit - maxL).toDouble())
                    val eFg = exp((fgLogit - maxL).toDouble())
                    hairProb[i] = (eFg / (eBg + eFg)).toFloat()
                }
                val hairUp = bilinearUpscaleFloat(hairProb, 512, 512, w, h)
                for (i in upscaled.indices) {
                    upscaled[i] = max(upscaled[i], hairUp[i] * 0.96f)
                }
            }
            personAlpha = upscaled
            personRatio = upscaled.count { it > 0.5f }.toFloat() / upscaled.size
        }

        val shouldRunObject = subjectType == SubjectType.OBJECT ||
            (subjectType == SubjectType.AUTO && (personRatio < 0.045f || personRatio > 0.90f))

        if (shouldRunObject) {
            onProgress(0.64f, "Object AI model chal raha hai...")
            val deeplabIn = LiteRtModelPool.bitmapToFloatRgbMinus1To1(source, 257, 257)
            val deeplabOut = modelPool.runModel(LiteRtModelPool.MODEL_DEEPLAB, 257, 257, 3, deeplabIn)
            val vocProb = FloatArray(257 * 257)
            for (i in 0 until 257 * 257) {
                val base = i * 21
                var maxVal = deeplabOut[base]
                var maxIdx = 0
                for (c in 1 until 21) {
                    val v = deeplabOut[base + c]
                    if (v > maxVal) {
                        maxVal = v
                        maxIdx = c
                    }
                }
                // Soft probability for foreground vs background class 0
                var sumExp = 0.0
                for (c in 0 until 21) {
                    sumExp += exp((deeplabOut[base + c] - maxVal).toDouble())
                }
                val bgProb = exp((deeplabOut[base] - maxVal).toDouble()) / sumExp
                vocProb[i] = (1.0 - bgProb).toFloat().let { if (maxIdx == 0 && it < 0.45f) it * 0.5f else it }
            }
            val vocUp = bilinearUpscaleFloat(vocProb, 257, 257, w, h)
            val vocCoverage = vocUp.count { it > 0.5f }.toFloat() / vocUp.size

            onProgress(0.78f, "Edge contrast aur subject refine ho raha hai...")
            val salient = computeSalientObjectMask(source, if (quality == QualityMode.HIGH) 220 else 150)

            objectAlpha = if (vocCoverage in 0.04f..0.88f) {
                // Fuse DeepLab object probability with salient contrast boundary
                FloatArray(w * h) { idx ->
                    val fused = vocUp[idx] * 0.68f + salient[idx] * 0.32f
                    smoothStep(0.36f, 0.62f, fused)
                }
            } else {
                salient
            }
            objectRatio = objectAlpha.count { it > 0.5f }.toFloat() / objectAlpha.size
        }

        onProgress(0.90f, "Cutout mask taiyaar ho raha hai...")
        val chosenAlpha: FloatArray
        val label: String
        val found: Boolean

        when (subjectType) {
            SubjectType.PERSON -> {
                chosenAlpha = personAlpha ?: FloatArray(w * h)
                found = personRatio in 0.015f..0.96f
                label = if (found) "Person mila" else "Person nahi mila"
            }
            SubjectType.OBJECT -> {
                chosenAlpha = objectAlpha ?: FloatArray(w * h)
                found = objectRatio in 0.02f..0.94f
                label = if (found) "Object mila" else "Object saaf nahi mila"
            }
            SubjectType.AUTO -> {
                if (personAlpha != null && personRatio in 0.045f..0.90f) {
                    chosenAlpha = personAlpha
                    found = true
                    label = "Person auto-detect hua"
                } else if (objectAlpha != null && objectRatio in 0.02f..0.94f) {
                    chosenAlpha = objectAlpha
                    found = true
                    label = "Object auto-detect hua"
                } else {
                    chosenAlpha = personAlpha ?: objectAlpha ?: FloatArray(w * h)
                    found = (personRatio > 0.015f || objectRatio > 0.02f)
                    label = if (found) "Subject mila" else "Koi saaf subject nahi mila"
                }
            }
        }

        val refined = guidedAlphaSnap(source, chosenAlpha)
        val maskBmp = floatArrayToMaskBitmap(refined, w, h)
        onProgress(1f, "Taiyaar!")
        SegmentationResult(maskBmp, label, found)
    }

    /**
     * Border-GMM + center-prior salient object detector for arbitrary products, food, and objects.
     */
    private fun computeSalientObjectMask(source: Bitmap, workMax: Int): FloatArray {
        val origW = source.width
        val origH = source.height
        val scale = min(1f, workMax.toFloat() / max(origW, origH))
        val sw = (origW * scale).roundToInt().coerceAtLeast(32)
        val sh = (origH * scale).roundToInt().coerceAtLeast(32)
        val small = Bitmap.createScaledBitmap(source, sw, sh, true)
        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        if (small !== source) small.recycle()

        // Sample border pixels as background color clusters (top, bottom, left, right strips)
        val borderColors = ArrayList<Int>(sw * 4 + sh * 4)
        for (x in 0 until sw) {
            borderColors.add(pixels[x])
            borderColors.add(pixels[(sh - 1) * sw + x])
        }
        for (y in 0 until sh) {
            borderColors.add(pixels[y * sw])
            borderColors.add(pixels[y * sw + (sw - 1)])
        }

        // Quantize border into up to 6 representative color centroids
        val bgSamples = borderColors.chunked(max(1, borderColors.size / 36)).map { chunk ->
            var sr = 0L; var sg = 0L; var sb = 0L
            for (c in chunk) {
                sr += (c shr 16) and 0xFF
                sg += (c shr 8) and 0xFF
                sb += c and 0xFF
            }
            val n = chunk.size.coerceAtLeast(1)
            floatArrayOf(sr.toFloat() / n, sg.toFloat() / n, sb.toFloat() / n)
        }

        val prob = FloatArray(sw * sh)
        val cx = sw * 0.5f
        val cy = sh * 0.5f
        val maxDist = hypot(cx, cy).coerceAtLeast(1f)

        for (y in 0 until sh) {
            for (x in 0 until sw) {
                val p = pixels[y * sw + x]
                val r = ((p shr 16) and 0xFF).toFloat()
                val g = ((p shr 8) and 0xFF).toFloat()
                val b = (p and 0xFF).toFloat()

                var minColorDist = Float.MAX_VALUE
                for (bg in bgSamples) {
                    val dr = r - bg[0]
                    val dg = g - bg[1]
                    val db = b - bg[2]
                    val d = sqrt(dr * dr + dg * dg + db * db)
                    if (d < minColorDist) minColorDist = d
                }
                val colorSal = (minColorDist / 95f).coerceIn(0f, 1.3f)
                val radial = 1f - (hypot(x - cx, y - cy) / maxDist)
                val centerPrior = (0.35f + 0.65f * radial).coerceIn(0.2f, 1f)
                // Edge penalty right on frame borders
                val borderFade = min(
                    min(x / (sw * 0.06f + 1f), (sw - 1 - x) / (sw * 0.06f + 1f)),
                    min(y / (sh * 0.06f + 1f), (sh - 1 - y) / (sh * 0.06f + 1f))
                ).coerceIn(0f, 1f)
                prob[y * sw + x] = smoothStep(0.28f, 0.66f, colorSal * centerPrior * (0.4f + 0.6f * borderFade))
            }
        }

        // 2 passes of neighborhood smoothing
        val smoothed = boxBlurFloat(prob, sw, sh, 2)
        return bilinearUpscaleFloat(smoothed, sw, sh, origW, origH)
    }

    /**
     * Snaps noisy probability edges gently toward strong image color boundaries.
     */
    private fun guidedAlphaSnap(source: Bitmap, alpha: FloatArray): FloatArray {
        val w = source.width
        val h = source.height
        val out = alpha.copyOf()
        val px = IntArray(w * h)
        source.getPixels(px, 0, w, 0, 0, w, h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val a = alpha[idx]
                if (a in 0.08f..0.92f) {
                    var sumW = 0f
                    var sumA = 0f
                    val c0 = px[idx]
                    val r0 = (c0 shr 16) and 0xFF
                    val g0 = (c0 shr 8) and 0xFF
                    val b0 = c0 and 0xFF
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val nIdx = (y + dy) * w + (x + dx)
                            val cn = px[nIdx]
                            val diff = abs(r0 - ((cn shr 16) and 0xFF)) +
                                abs(g0 - ((cn shr 8) and 0xFF)) +
                                abs(b0 - (cn and 0xFF))
                            val weight = exp((-diff / 42f).toDouble()).toFloat()
                            sumW += weight
                            sumA += weight * alpha[nIdx]
                        }
                    }
                    out[idx] = (sumA / sumW.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                }
            }
        }
        return out
    }

    suspend fun applyCutoutWithRefinement(
        source: Bitmap,
        rawMask: Bitmap,
        edgeSettings: EdgeSettings
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = source.width
        val h = source.height
        val srcPx = IntArray(w * h)
        source.getPixels(srcPx, 0, w, 0, 0, w, h)

        val scaledMask = if (rawMask.width == w && rawMask.height == h) rawMask
        else Bitmap.createScaledBitmap(rawMask, w, h, true)
        val maskPx = IntArray(w * h)
        scaledMask.getPixels(maskPx, 0, w, 0, 0, w, h)
        if (scaledMask !== rawMask) scaledMask.recycle()

        var alpha = FloatArray(w * h) { ((maskPx[it] ushr 24) and 0xFF) / 255f }

        // 1. Grow / Shrink
        val gs = edgeSettings.growShrink.roundToInt()
        if (gs != 0) {
            alpha = morphFloatMask(alpha, w, h, gs)
        }

        // 2. Smooth + Feather
        val blurRadius = ((edgeSettings.smooth + edgeSettings.feather * 0.6f) / 2f).roundToInt().coerceIn(0, 10)
        if (blurRadius > 0) {
            alpha = boxBlurFloat(alpha, w, h, blurRadius)
        }

        // 3. Hair crispness / contrast boost
        if (!edgeSettings.hairDetailMode && edgeSettings.feather < 3f) {
            for (i in alpha.indices) {
                alpha[i] = smoothStep(0.18f, 0.82f, alpha[i])
            }
        }

        // 4. Color de-fringing near semi-transparent boundary
        val defringe = edgeSettings.defringeStrength.coerceIn(0f, 1f)
        val outPx = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val a = (alpha[idx] * 255f).roundToInt().coerceIn(0, 255)
                if (a == 0) {
                    outPx[idx] = 0
                    continue
                }
                var r = (srcPx[idx] ushr 16) and 0xFF
                var g = (srcPx[idx] ushr 8) and 0xFF
                var b = srcPx[idx] and 0xFF

                if (defringe > 0.05f && a in 12..235) {
                    // Pull semi-transparent edge color toward nearest solid foreground neighbor
                    var fr = 0f; var fg = 0f; var fb = 0f; var fw = 0f
                    val rad = 3
                    for (dy in -rad..rad) {
                        val ny = y + dy
                        if (ny !in 0 until h) continue
                        for (dx in -rad..rad) {
                            val nx = x + dx
                            if (nx !in 0 until w) continue
                            val nIdx = ny * w + nx
                            val na = alpha[nIdx]
                            if (na > 0.85f) {
                                val np = srcPx[nIdx]
                                fr += (np ushr 16) and 0xFF
                                fg += (np ushr 8) and 0xFF
                                fb += np and 0xFF
                                fw += 1f
                            }
                        }
                    }
                    if (fw > 0f) {
                        val blend = defringe * (1f - a / 255f)
                        r = (r * (1f - blend) + (fr / fw) * blend).roundToInt().coerceIn(0, 255)
                        g = (g * (1f - blend) + (fg / fw) * blend).roundToInt().coerceIn(0, 255)
                        b = (b * (1f - blend) + (fb / fw) * blend).roundToInt().coerceIn(0, 255)
                    }
                }
                outPx[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        Bitmap.createBitmap(outPx, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Paints Erase, Restore, or Smart Edge-Snapping Brush directly on a mutable mask bitmap.
     */
    fun applyBrushStroke(
        source: Bitmap,
        mask: Bitmap,
        normX: Float,
        normY: Float,
        radiusNorm: Float,
        hardness: Float,
        isRestore: Boolean,
        smartEdgeSnap: Boolean
    ) {
        val w = mask.width
        val h = mask.height
        val cx = (normX * w).roundToInt()
        val cy = (normY * h).roundToInt()
        val radPx = (radiusNorm * min(w, h)).roundToInt().coerceAtLeast(3)

        val x0 = (cx - radPx).coerceAtLeast(0)
        val y0 = (cy - radPx).coerceAtLeast(0)
        val x1 = (cx + radPx).coerceAtMost(w - 1)
        val y1 = (cy + radPx).coerceAtMost(h - 1)
        val bw = x1 - x0 + 1
        val bh = y1 - y0 + 1
        if (bw <= 0 || bh <= 0) return

        val mPx = IntArray(bw * bh)
        mask.getPixels(mPx, 0, bw, x0, y0, bw, bh)

        var centerR = 0; var centerG = 0; var centerB = 0
        val sPx = if (smartEdgeSnap && source.width == w && source.height == h) {
            IntArray(bw * bh).also {
                source.getPixels(it, 0, bw, x0, y0, bw, bh)
                val cp = it[(cy - y0).coerceIn(0, bh - 1) * bw + (cx - x0).coerceIn(0, bw - 1)]
                centerR = (cp shr 16) and 0xFF
                centerG = (cp shr 8) and 0xFF
                centerB = cp and 0xFF
            }
        } else null

        val targetAlpha = if (isRestore) 255 else 0
        for (y in y0..y1) {
            for (x in x0..x1) {
                val dist = hypot((x - cx).toFloat(), (y - cy).toFloat()) / radPx
                if (dist > 1f) continue
                var strength = if (hardness >= 0.95f) 1f else {
                    val softStart = hardness.coerceIn(0f, 0.94f)
                    if (dist <= softStart) 1f
                    else 1f - ((dist - softStart) / (1f - softStart))
                }
                val idx = (y - y0) * bw + (x - x0)
                if (sPx != null) {
                    val p = sPx[idx]
                    val diff = abs(((p shr 16) and 0xFF) - centerR) +
                        abs(((p shr 8) and 0xFF) - centerG) +
                        abs((p and 0xFF) - centerB)
                    // Snap away from contrasting edge boundary
                    val sim = exp((-diff / 65f).toDouble()).toFloat()
                    strength *= sim
                }
                val oldA = (mPx[idx] ushr 24) and 0xFF
                val newA = (oldA * (1f - strength) + targetAlpha * strength).roundToInt().coerceIn(0, 255)
                mPx[idx] = (newA shl 24) or (newA shl 16) or (newA shl 8) or newA
            }
        }
        mask.setPixels(mPx, 0, bw, x0, y0, bw, bh)
    }

    /**
     * Tap-to-select a color-contiguous region on the photo to keep (255) or remove (0).
     */
    suspend fun applyTapSelectRegion(
        source: Bitmap,
        mask: Bitmap,
        normX: Float,
        normY: Float,
        keepRegion: Boolean,
        tolerance: Int = 46
    ) = withContext(Dispatchers.Default) {
        val w = mask.width
        val h = mask.height
        val sx = (normX * w).roundToInt().coerceIn(0, w - 1)
        val sy = (normY * h).roundToInt().coerceIn(0, h - 1)
        val src = if (source.width == w && source.height == h) source else Bitmap.createScaledBitmap(source, w, h, true)
        val sPx = IntArray(w * h)
        src.getPixels(sPx, 0, w, 0, 0, w, h)
        if (src !== source) src.recycle()

        val mPx = IntArray(w * h)
        mask.getPixels(mPx, 0, w, 0, 0, w, h)

        val seed = sPx[sy * w + sx]
        val sr = (seed shr 16) and 0xFF
        val sg = (seed shr 8) and 0xFF
        val sb = seed and 0xFF

        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        val startIdx = sy * w + sx
        queue[tail++] = startIdx
        visited[startIdx] = true

        val targetVal = if (keepRegion) 255 else 0
        val fillColor = (targetVal shl 24) or (targetVal shl 16) or (targetVal shl 8) or targetVal
        val maxVisit = (w * h * 0.45f).toInt()
        var count = 0

        while (head < tail && count < maxVisit) {
            val idx = queue[head++]
            count++
            mPx[idx] = fillColor
            val x = idx % w
            val y = idx / w
            val neighbors = intArrayOf(
                if (x > 0) idx - 1 else -1,
                if (x < w - 1) idx + 1 else -1,
                if (y > 0) idx - w else -1,
                if (y < h - 1) idx + w else -1
            )
            for (n in neighbors) {
                if (n >= 0 && !visited[n]) {
                    val np = sPx[n]
                    val diff = maxOf(
                        abs(((np shr 16) and 0xFF) - sr),
                        abs(((np shr 8) and 0xFF) - sg),
                        abs((np and 0xFF) - sb)
                    )
                    if (diff <= tolerance) {
                        visited[n] = true
                        queue[tail++] = n
                    }
                }
            }
        }
        mask.setPixels(mPx, 0, w, 0, 0, w, h)
    }

    /**
     * Applies a lasso polygon selection onto the mask (keep=true sets alpha=255, keep=false sets alpha=0).
     */
    fun applyLassoPolygon(
        mask: Bitmap,
        normalizedPoints: List<Pair<Float, Float>>,
        keepInside: Boolean
    ) {
        if (normalizedPoints.size < 3) return
        val canvas = Canvas(mask)
        val path = Path().apply {
            val first = normalizedPoints.first()
            moveTo(first.first * mask.width, first.second * mask.height)
            for (i in 1 until normalizedPoints.size) {
                val pt = normalizedPoints[i]
                lineTo(pt.first * mask.width, pt.second * mask.height)
            }
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            if (keepInside) {
                color = Color.WHITE
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
            } else {
                color = Color.TRANSPARENT
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
        }
        canvas.drawPath(path, paint)
    }

    private fun morphFloatMask(alpha: FloatArray, w: Int, h: Int, deltaPx: Int): FloatArray {
        val radius = abs(deltaPx).coerceAtMost(12)
        val isDilate = deltaPx > 0
        var cur = alpha
        repeat(radius) {
            val next = FloatArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var ext = if (isDilate) 0f else 1f
                    for (dy in -1..1) {
                        val ny = (y + dy).coerceIn(0, h - 1)
                        for (dx in -1..1) {
                            val nx = (x + dx).coerceIn(0, w - 1)
                            val v = cur[ny * w + nx]
                            ext = if (isDilate) max(ext, v) else min(ext, v)
                        }
                    }
                    next[y * w + x] = ext
                }
            }
            cur = next
        }
        return cur
    }

    private fun boxBlurFloat(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        if (radius <= 0) return src
        val temp = FloatArray(w * h)
        val out = FloatArray(w * h)
        val window = radius * 2 + 1
        // Horizontal pass
        for (y in 0 until h) {
            val rowOff = y * w
            var sum = 0f
            for (dx in -radius..radius) {
                sum += src[rowOff + dx.coerceIn(0, w - 1)]
            }
            for (x in 0 until w) {
                temp[rowOff + x] = sum / window
                val left = (x - radius).coerceIn(0, w - 1)
                val right = (x + radius + 1).coerceIn(0, w - 1)
                sum += src[rowOff + right] - src[rowOff + left]
            }
        }
        // Vertical pass
        for (x in 0 until w) {
            var sum = 0f
            for (dy in -radius..radius) {
                sum += temp[dy.coerceIn(0, h - 1) * w + x]
            }
            for (y in 0 until h) {
                out[y * w + x] = sum / window
                val top = (y - radius).coerceIn(0, h - 1)
                val bottom = (y + radius + 1).coerceIn(0, h - 1)
                sum += temp[bottom * w + x] - temp[top * w + x]
            }
        }
        return out
    }

    private fun bilinearUpscaleFloat(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        if (sw == dw && sh == dh) return src.copyOf()
        val dst = FloatArray(dw * dh)
        val rx = (sw - 1).toFloat() / max(1, dw - 1)
        val ry = (sh - 1).toFloat() / max(1, dh - 1)
        for (y in 0 until dh) {
            val fy = y * ry
            val y0 = fy.toInt().coerceIn(0, sh - 1)
            val y1 = (y0 + 1).coerceAtMost(sh - 1)
            val wy = fy - y0
            for (x in 0 until dw) {
                val fx = x * rx
                val x0 = fx.toInt().coerceIn(0, sw - 1)
                val x1 = (x0 + 1).coerceAtMost(sw - 1)
                val wx = fx - x0
                val v00 = src[y0 * sw + x0]
                val v10 = src[y0 * sw + x1]
                val v01 = src[y1 * sw + x0]
                val v11 = src[y1 * sw + x1]
                val top = v00 + (v10 - v00) * wx
                val bot = v01 + (v11 - v01) * wx
                dst[y * dw + x] = (top + (bot - top) * wy).coerceIn(0f, 1f)
            }
        }
        return dst
    }

    private fun floatArrayToMaskBitmap(alpha: FloatArray, w: Int, h: Int): Bitmap {
        val px = IntArray(w * h)
        for (i in px.indices) {
            val a = (alpha[i] * 255f).roundToInt().coerceIn(0, 255)
            px[i] = (a shl 24) or (a shl 16) or (a shl 8) or a
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
