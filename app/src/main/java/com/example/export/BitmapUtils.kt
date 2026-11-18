package com.example.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.res.ResourcesCompat
import androidx.exifinterface.media.ExifInterface
import com.example.data.model.AdjustmentParams
import com.example.data.model.BackgroundSpec
import com.example.data.model.BackgroundType
import com.example.data.model.BuiltinBackgrounds
import com.example.data.model.CanvasPreset
import com.example.data.model.ImageFilter
import com.example.data.model.LayerTransform
import com.example.data.model.StickerOverlay
import com.example.data.model.TextOverlay
import com.example.ui.theme.OverlayFontOptions
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object BitmapUtils {

    fun decodeUriSafe(context: Context, uri: Uri, maxDim: Int?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        if (maxDim != null) {
            val longest = max(bounds.outWidth, bounds.outHeight)
            while (longest / sampleSize > maxDim * 1.4f) {
                sampleSize *= 2
            }
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val oriented = applyExifOrientation(context, uri, decoded)
        return if (maxDim != null && max(oriented.width, oriented.height) > maxDim) {
            scaleDownToMax(oriented, maxDim).also {
                if (it !== oriented) oriented.recycle()
            }
        } else {
            oriented
        }
    }

    fun decodeDrawableSafe(context: Context, resId: Int, maxDim: Int?): Bitmap? {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = BitmapFactory.decodeResource(context.resources, resId, opts) ?: return null
        return if (maxDim != null && max(bmp.width, bmp.height) > maxDim) {
            scaleDownToMax(bmp, maxDim).also { if (it !== bmp) bmp.recycle() }
        } else {
            bmp
        }
    }

    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    fun scaleDownToMax(src: Bitmap, maxDim: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxDim) return src
        val ratio = maxDim.toFloat() / longest
        val w = (src.width * ratio).roundToInt().coerceAtLeast(1)
        val h = (src.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    fun applyAdjustmentsAndFilter(src: Bitmap, adj: AdjustmentParams): Bitmap {
        // Crop + Rotation first
        val left = (adj.cropLeft.coerceIn(0f, 0.9f) * src.width).roundToInt()
        val top = (adj.cropTop.coerceIn(0f, 0.9f) * src.height).roundToInt()
        val right = (adj.cropRight.coerceIn(adj.cropLeft + 0.05f, 1f) * src.width).roundToInt()
        val bottom = (adj.cropBottom.coerceIn(adj.cropTop + 0.05f, 1f) * src.height).roundToInt()
        val cw = (right - left).coerceAtLeast(2)
        val ch = (bottom - top).coerceAtLeast(2)

        val cropped = if (left == 0 && top == 0 && cw == src.width && ch == src.height && adj.canvasRotation == 0) {
            src.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            val m = Matrix().apply {
                if (adj.canvasRotation != 0) postRotate(adj.canvasRotation.toFloat())
            }
            Bitmap.createBitmap(src, left, top, cw, ch, m, true)
        }

        val hasColorWork = adj.brightness != 0f || adj.contrast != 1f || adj.saturation != 1f || adj.filter != ImageFilter.NONE
        val colored = if (!hasColorWork) {
            cropped
        } else {
            val out = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val cm = ColorMatrix()

            // Filter base
            when (adj.filter) {
                ImageFilter.NONE -> {}
                ImageFilter.VIVID -> {
                    cm.setSaturation(1.35f)
                }
                ImageFilter.WARM -> {
                    cm.setScale(1.08f, 1.02f, 0.90f, 1f)
                }
                ImageFilter.COOL -> {
                    cm.setScale(0.92f, 1.0f, 1.12f, 1f)
                }
                ImageFilter.BW -> {
                    cm.setSaturation(0f)
                }
                ImageFilter.CINEMA -> {
                    cm.setSaturation(0.85f)
                    val tint = ColorMatrix()
                    tint.setScale(0.98f, 1.03f, 1.06f, 1f)
                    cm.postConcat(tint)
                }
            }

            // Saturation
            if (adj.saturation != 1f) {
                val sat = ColorMatrix().apply { setSaturation(adj.saturation) }
                cm.postConcat(sat)
            }

            // Contrast & Brightness
            val c = adj.contrast
            val translate = (-0.5f * c + 0.5f) * 255f + adj.brightness
            val bc = ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, translate,
                    0f, c, 0f, 0f, translate,
                    0f, 0f, c, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(bc)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(cm)
            }
            canvas.drawBitmap(cropped, 0f, 0f, paint)
            if (cropped !== src) cropped.recycle()
            out
        }

        return if (adj.sharpness > 0.05f) {
            applyUnsharpMask(colored, adj.sharpness).also {
                if (it !== colored) colored.recycle()
            }
        } else {
            colored
        }
    }

    private fun applyUnsharpMask(src: Bitmap, amount: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = px.copyOf()
        val strength = amount.coerceIn(0f, 1f) * 1.4f
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val c = px[i]
                val a = (c ushr 24) and 0xFF
                if (a == 0) continue
                val c1 = px[i - 1]; val c2 = px[i + 1]; val c3 = px[i - w]; val c4 = px[i + w]
                val avgR = (((c1 shr 16) and 0xFF) + ((c2 shr 16) and 0xFF) + ((c3 shr 16) and 0xFF) + ((c4 shr 16) and 0xFF)) / 4f
                val avgG = (((c1 shr 8) and 0xFF) + ((c2 shr 8) and 0xFF) + ((c3 shr 8) and 0xFF) + ((c4 shr 8) and 0xFF)) / 4f
                val avgB = ((c1 and 0xFF) + (c2 and 0xFF) + (c3 and 0xFF) + (c4 and 0xFF)) / 4f
                val r = (((c shr 16) and 0xFF) + (((c shr 16) and 0xFF) - avgR) * strength).roundToInt().coerceIn(0, 255)
                val g = (((c shr 8) and 0xFF) + (((c shr 8) and 0xFF) - avgG) * strength).roundToInt().coerceIn(0, 255)
                val b = ((c and 0xFF) + ((c and 0xFF) - avgB) * strength).roundToInt().coerceIn(0, 255)
                out[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * "Mix karo": Harmonizes subject brightness and white-balance tint to match target background.
     */
    fun harmonizeSubjectToBackground(subject: Bitmap, background: Bitmap): Bitmap {
        val bgSample = scaleDownToMax(background, 64)
        val bgPx = IntArray(bgSample.width * bgSample.height)
        bgSample.getPixels(bgPx, 0, bgSample.width, 0, 0, bgSample.width, bgSample.height)
        if (bgSample !== background) bgSample.recycle()

        var bgR = 0f; var bgG = 0f; var bgB = 0f
        for (p in bgPx) {
            bgR += (p shr 16) and 0xFF
            bgG += (p shr 8) and 0xFF
            bgB += p and 0xFF
        }
        val bgN = bgPx.size.coerceAtLeast(1)
        bgR /= bgN; bgG /= bgN; bgB /= bgN

        val subSample = scaleDownToMax(subject, 64)
        val subPx = IntArray(subSample.width * subSample.height)
        subSample.getPixels(subPx, 0, subSample.width, 0, 0, subSample.width, subSample.height)
        if (subSample !== subject) subSample.recycle()

        var sR = 0f; var sG = 0f; var sB = 0f; var sN = 0
        for (p in subPx) {
            if (((p ushr 24) and 0xFF) > 128) {
                sR += (p shr 16) and 0xFF
                sG += (p shr 8) and 0xFF
                sB += p and 0xFF
                sN++
            }
        }
        if (sN == 0) return subject.copy(Bitmap.Config.ARGB_8888, true)
        sR /= sN; sG /= sN; sB /= sN

        val bgLum = (bgR * 0.299f + bgG * 0.587f + bgB * 0.114f)
        val subLum = (sR * 0.299f + sG * 0.587f + sB * 0.114f).coerceAtLeast(1f)
        val lumRatio = (bgLum / subLum).coerceIn(0.75f, 1.28f)
        val blendedRatio = 1f + (lumRatio - 1f) * 0.35f

        val shiftR = ((bgR - sR) * 0.18f).coerceIn(-24f, 24f)
        val shiftG = ((bgG - sG) * 0.18f).coerceIn(-24f, 24f)
        val shiftB = ((bgB - sB) * 0.18f).coerceIn(-24f, 24f)

        val out = Bitmap.createBitmap(subject.width, subject.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix(
            floatArrayOf(
                blendedRatio, 0f, 0f, 0f, shiftR,
                0f, blendedRatio, 0f, 0f, shiftG,
                0f, 0f, blendedRatio, 0f, shiftB,
                0f, 0f, 0f, 1f, 0f
            )
        )
        Canvas(out).drawBitmap(subject, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }

    fun renderBackgroundBitmap(
        context: Context,
        width: Int,
        height: Int,
        original: Bitmap,
        spec: BackgroundSpec,
        cleanBgOverride: Bitmap? = null
    ): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        when (spec.type) {
            BackgroundType.TRANSPARENT -> {
                if (cleanBgOverride != null) {
                    drawBitmapCover(canvas, cleanBgOverride, width, height)
                }
            }
            BackgroundType.SOLID -> {
                canvas.drawColor(spec.solidColorArgb)
            }
            BackgroundType.GRADIENT -> {
                val p = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, width.toFloat(), height.toFloat(),
                        spec.gradientStartArgb, spec.gradientEndArgb, Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
            }
            BackgroundType.BUILTIN_STUDIO -> {
                val opt = BuiltinBackgrounds.firstOrNull { it.id == spec.builtinId } ?: BuiltinBackgrounds.first()
                if (opt.drawableRes != null) {
                    val bmp = decodeDrawableSafe(context, opt.drawableRes, max(width, height))
                    if (bmp != null) {
                        drawBitmapCover(canvas, bmp, width, height)
                        bmp.recycle()
                    }
                } else {
                    drawPatternBackground(canvas, width, height, opt.patternType ?: "dots", opt.primaryColor, opt.secondaryColor)
                }
            }
            BackgroundType.BLURRED_ORIGINAL -> {
                val blurred = fastBoxBlur(scaleDownToMax(original, 220), (spec.blurRadius / 2f).roundToInt().coerceIn(2, 22))
                drawBitmapCover(canvas, blurred, width, height)
                blurred.recycle()
            }
            BackgroundType.GALLERY_IMAGE -> {
                val customBmp = spec.customImagePath?.let { BitmapFactory.decodeFile(it) }
                if (customBmp != null) {
                    drawBitmapCover(canvas, customBmp, width, height)
                    customBmp.recycle()
                } else {
                    canvas.drawColor(Color.DKGRAY)
                }
            }
        }
        return out
    }

    private fun drawPatternBackground(
        canvas: Canvas,
        w: Int,
        h: Int,
        patternType: String,
        c1: androidx.compose.ui.graphics.Color,
        c2: androidx.compose.ui.graphics.Color
    ) {
        val argb1 = android.graphics.Color.argb((c1.alpha * 255).toInt(), (c1.red * 255).toInt(), (c1.green * 255).toInt(), (c1.blue * 255).toInt())
        val argb2 = android.graphics.Color.argb((c2.alpha * 255).toInt(), (c2.red * 255).toInt(), (c2.green * 255).toInt(), (c2.blue * 255).toInt())
        canvas.drawColor(argb1)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb2 }
        when (patternType) {
            "dots" -> {
                p.alpha = 70
                val step = max(28f, w / 12f)
                var y = step / 2f
                while (y < h) {
                    var x = step / 2f
                    while (x < w) {
                        canvas.drawCircle(x, y, step * 0.16f, p)
                        x += step
                    }
                    y += step
                }
            }
            "grid" -> {
                p.alpha = 90
                p.strokeWidth = max(2f, w / 360f)
                val step = max(36f, w / 10f)
                var x = 0f
                while (x <= w) { canvas.drawLine(x, 0f, x, h.toFloat(), p); x += step }
                var y = 0f
                while (y <= h) { canvas.drawLine(0f, y, w.toFloat(), y, p); y += step }
            }
            "stripes" -> {
                p.alpha = 55
                p.strokeWidth = max(12f, w / 28f)
                val step = max(48f, w / 9f)
                var x = -h.toFloat()
                while (x < w + h) {
                    canvas.drawLine(x, 0f, x + h, h.toFloat(), p)
                    x += step
                }
            }
            else -> {
                val radPaint = Paint().apply {
                    shader = RadialGradient(w * 0.5f, h * 0.45f, max(w, h) * 0.7f, argb1, argb2, Shader.TileMode.CLAMP)
                }
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), radPaint)
            }
        }
    }

    fun composeCutoutWithBackground(
        context: Context,
        original: Bitmap,
        cutout: Bitmap,
        bgSpec: BackgroundSpec,
        subjectTransform: LayerTransform = LayerTransform(),
        bgTransform: LayerTransform = LayerTransform(),
        cleanBgOverride: Bitmap? = null,
        shadowEnabled: Boolean = false,
        shadowOpacity: Float = 0.45f,
        outlineEnabled: Boolean = false,
        outlineThickness: Float = 10f,
        outlineColorArgb: Int = Color.WHITE,
        mixKaroEnabled: Boolean = false,
        canvasPreset: CanvasPreset = CanvasPreset.ORIGINAL,
        customW: Int = 1080,
        customH: Int = 1080,
        textOverlays: List<TextOverlay> = emptyList(),
        stickerOverlays: List<StickerOverlay> = emptyList()
    ): Bitmap {
        val (outW, outH) = resolveCanvasDimensions(cutout.width, cutout.height, canvasPreset, customW, customH)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val bgBmp = if (bgSpec.type == BackgroundType.TRANSPARENT && cleanBgOverride == null) {
            null
        } else {
            renderBackgroundBitmap(context, outW, outH, original, bgSpec, cleanBgOverride)
        }

        if (bgBmp != null) {
            drawTransformedLayer(canvas, bgBmp, outW, outH, bgTransform, fitCover = true)
        }

        // Harmonize subject if Mix Karo is active and a non-transparent background exists
        val activeCutout = if (mixKaroEnabled && bgBmp != null) {
            harmonizeSubjectToBackground(cutout, bgBmp)
        } else cutout

        // Sticker outline
        if (outlineEnabled) {
            val outlined = addStickerOutline(activeCutout, outlineThickness, outlineColorArgb)
            if (shadowEnabled) {
                drawSubjectShadow(canvas, outlined, outW, outH, subjectTransform, shadowOpacity)
            }
            drawTransformedLayer(canvas, outlined, outW, outH, subjectTransform, fitCover = false)
            outlined.recycle()
        } else {
            if (shadowEnabled) {
                drawSubjectShadow(canvas, activeCutout, outW, outH, subjectTransform, shadowOpacity)
            }
            drawTransformedLayer(canvas, activeCutout, outW, outH, subjectTransform, fitCover = false)
        }

        if (activeCutout !== cutout) activeCutout.recycle()
        bgBmp?.recycle()

        // Text & Sticker Overlays
        drawOverlays(context, canvas, outW, outH, textOverlays, stickerOverlays)

        return out
    }

    fun resolveCanvasDimensions(
        baseW: Int,
        baseH: Int,
        preset: CanvasPreset,
        customW: Int,
        customH: Int
    ): Pair<Int, Int> {
        return when (preset) {
            CanvasPreset.ORIGINAL -> baseW to baseH
            CanvasPreset.CUSTOM -> customW.coerceIn(256, 4096) to customH.coerceIn(256, 4096)
            else -> {
                if (preset.exactW != null && preset.exactH != null) {
                    val scale = min(1f, max(baseW, baseH).toFloat() / max(preset.exactW, preset.exactH).toFloat())
                    val targetMax = max(max(baseW, baseH), max(preset.exactW, preset.exactH))
                    if (targetMax == max(preset.exactW, preset.exactH)) {
                        preset.exactW to preset.exactH
                    } else {
                        val r = preset.ratioW.toFloat() / preset.ratioH.toFloat()
                        if (r >= 1f) baseW to (baseW / r).roundToInt().coerceAtLeast(1)
                        else (baseH * r).roundToInt().coerceAtLeast(1) to baseH
                    }
                } else {
                    val r = preset.ratioW.toFloat() / preset.ratioH.toFloat()
                    val area = baseW.toLong() * baseH.toLong()
                    val targetH = kotlin.math.sqrt(area / r).roundToInt().coerceAtLeast(64)
                    val targetW = (targetH * r).roundToInt().coerceAtLeast(64)
                    targetW to targetH
                }
            }
        }
    }

    private fun drawTransformedLayer(
        canvas: Canvas,
        bmp: Bitmap,
        canvasW: Int,
        canvasH: Int,
        t: LayerTransform,
        fitCover: Boolean
    ) {
        val baseScale = if (fitCover) {
            max(canvasW.toFloat() / bmp.width, canvasH.toFloat() / bmp.height)
        } else {
            min(canvasW.toFloat() / bmp.width, canvasH.toFloat() / bmp.height)
        }
        val m = Matrix()
        m.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
        m.postScale(
            baseScale * t.scale * (if (t.flipH) -1f else 1f),
            baseScale * t.scale * (if (t.flipV) -1f else 1f)
        )
        m.postRotate(t.rotationDeg)
        m.postTranslate(
            canvasW / 2f + t.offsetX * canvasW,
            canvasH / 2f + t.offsetY * canvasH
        )
        canvas.drawBitmap(bmp, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawSubjectShadow(
        canvas: Canvas,
        subject: Bitmap,
        canvasW: Int,
        canvasH: Int,
        t: LayerTransform,
        opacity: Float
    ) {
        val small = scaleDownToMax(subject, 160)
        val w = small.width
        val h = small.height
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== subject) small.recycle()

        val shadowAlpha = IntArray(w * h)
        for (i in px.indices) {
            val a = ((px[i] ushr 24) and 0xFF) * opacity.coerceIn(0f, 1f)
            shadowAlpha[i] = (a.roundToInt().coerceIn(0, 255) shl 24)
        }
        val shadowRaw = Bitmap.createBitmap(shadowAlpha, w, h, Bitmap.Config.ARGB_8888)
        val blurredShadow = fastBoxBlur(shadowRaw, 7)
        shadowRaw.recycle()

        val shadowTransform = t.copy(
            offsetX = t.offsetX + 0.015f,
            offsetY = t.offsetY + 0.032f
        )
        val baseScale = min(canvasW.toFloat() / subject.width, canvasH.toFloat() / subject.height) *
            (subject.width.toFloat() / blurredShadow.width)
        val m = Matrix()
        m.postTranslate(-blurredShadow.width / 2f, -blurredShadow.height / 2f)
        m.postScale(
            baseScale * shadowTransform.scale * (if (shadowTransform.flipH) -1f else 1f),
            baseScale * shadowTransform.scale * (if (shadowTransform.flipV) -1f else 1f)
        )
        m.postRotate(shadowTransform.rotationDeg)
        m.postTranslate(
            canvasW / 2f + shadowTransform.offsetX * canvasW,
            canvasH / 2f + shadowTransform.offsetY * canvasH
        )
        canvas.drawBitmap(blurredShadow, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        blurredShadow.recycle()
    }

    fun addStickerOutline(subject: Bitmap, thicknessPx: Float, colorArgb: Int): Bitmap {
        val pad = thicknessPx.roundToInt().coerceIn(2, 36)
        val w = subject.width
        val h = subject.height
        val px = IntArray(w * h)
        subject.getPixels(px, 0, w, 0, 0, w, h)

        val alpha = IntArray(w * h) { (px[it] ushr 24) and 0xFF }
        val dilated = alpha.copyOf()
        val rSq = pad * pad
        val step = if (pad > 14) 2 else 1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (alpha[y * w + x] > 230) continue
                var maxA = alpha[y * w + x]
                val y0 = (y - pad).coerceAtLeast(0)
                val y1 = (y + pad).coerceAtMost(h - 1)
                val x0 = (x - pad).coerceAtLeast(0)
                val x1 = (x + pad).coerceAtMost(w - 1)
                for (ny in y0..y1 step step) {
                    val dy = ny - y
                    for (nx in x0..x1 step step) {
                        val dx = nx - x
                        if (dx * dx + dy * dy <= rSq) {
                            val na = alpha[ny * w + nx]
                            if (na > maxA) maxA = na
                        }
                    }
                }
                dilated[y * w + x] = maxA
            }
        }

        val rCol = (colorArgb shr 16) and 0xFF
        val gCol = (colorArgb shr 8) and 0xFF
        val bCol = colorArgb and 0xFF
        val outPx = IntArray(w * h)
        for (i in outPx.indices) {
            val fgA = (px[i] ushr 24) and 0xFF
            val dilA = dilated[i]
            if (fgA > 200) {
                outPx[i] = px[i]
            } else if (dilA > 30) {
                val fgW = fgA / 255f
                val fr = (px[i] shr 16) and 0xFF
                val fg = (px[i] shr 8) and 0xFF
                val fb = px[i] and 0xFF
                val rr = (rCol * (1f - fgW) + fr * fgW).roundToInt().coerceIn(0, 255)
                val gg = (gCol * (1f - fgW) + fg * fgW).roundToInt().coerceIn(0, 255)
                val bb = (bCol * (1f - fgW) + fb * fgW).roundToInt().coerceIn(0, 255)
                outPx[i] = (dilA shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
        }
        return Bitmap.createBitmap(outPx, w, h, Bitmap.Config.ARGB_8888)
    }

    fun drawOverlays(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        textOverlays: List<TextOverlay>,
        stickerOverlays: List<StickerOverlay>
    ) {
        val scaleFactor = min(w, h) / 400f
        for (st in stickerOverlays) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 44f * st.scale * scaleFactor
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(st.symbol, st.normX * w, st.normY * h, p)
        }
        for (t in textOverlays) {
            val fontOpt = OverlayFontOptions.firstOrNull { it.id == t.fontId }
            val typeface = fontOpt?.let {
                runCatching { ResourcesCompat.getFont(context, it.fontResId) }.getOrNull()
            } ?: Typeface.DEFAULT_BOLD
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = t.colorArgb
                textSize = t.sizeSp * scaleFactor
                this.typeface = typeface
                textAlign = Paint.Align.CENTER
                if (t.hasShadow) {
                    setShadowLayer(6f * scaleFactor, 2f * scaleFactor, 3f * scaleFactor, Color.argb(180, 0, 0, 0))
                }
            }
            canvas.drawText(t.text, t.normX * w, t.normY * h, p)
        }
    }

    fun trimTransparentBounds(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (((px[y * w + x] ushr 24) and 0xFF) > 12) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) return src
        return Bitmap.createBitmap(src, minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    fun fastBoxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        val out = IntArray(w * h)
        val win = radius * 2 + 1
        for (y in 0 until h) {
            val row = y * w
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (dx in -radius..radius) {
                val c = px[row + dx.coerceIn(0, w - 1)]
                sa += (c ushr 24) and 0xFF; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            for (x in 0 until w) {
                tmp[row + x] = ((sa / win) shl 24) or ((sr / win) shl 16) or ((sg / win) shl 8) or (sb / win)
                val l = px[row + (x - radius).coerceIn(0, w - 1)]
                val r = px[row + (x + radius + 1).coerceIn(0, w - 1)]
                sa += ((r ushr 24) and 0xFF) - ((l ushr 24) and 0xFF)
                sr += ((r shr 16) and 0xFF) - ((l shr 16) and 0xFF)
                sg += ((r shr 8) and 0xFF) - ((l shr 8) and 0xFF)
                sb += (r and 0xFF) - (l and 0xFF)
            }
        }
        for (x in 0 until w) {
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (dy in -radius..radius) {
                val c = tmp[dy.coerceIn(0, h - 1) * w + x]
                sa += (c ushr 24) and 0xFF; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            for (y in 0 until h) {
                out[y * w + x] = ((sa / win) shl 24) or ((sr / win) shl 16) or ((sg / win) shl 8) or (sb / win)
                val t = tmp[(y - radius).coerceIn(0, h - 1) * w + x]
                val b = tmp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                sa += ((b ushr 24) and 0xFF) - ((t ushr 24) and 0xFF)
                sr += ((b shr 16) and 0xFF) - ((t shr 16) and 0xFF)
                sg += ((b shr 8) and 0xFF) - ((t shr 8) and 0xFF)
                sb += (b and 0xFF) - (t and 0xFF)
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun drawBitmapCover(canvas: Canvas, bmp: Bitmap, dstW: Int, dstH: Int) {
        val srcRatio = bmp.width.toFloat() / bmp.height
        val dstRatio = dstW.toFloat() / dstH
        val srcRect = if (srcRatio > dstRatio) {
            val cropW = (bmp.height * dstRatio).roundToInt()
            val x0 = (bmp.width - cropW) / 2
            Rect(x0, 0, x0 + cropW, bmp.height)
        } else {
            val cropH = (bmp.width / dstRatio).roundToInt()
            val y0 = (bmp.height - cropH) / 2
            Rect(0, y0, bmp.width, y0 + cropH)
        }
        canvas.drawBitmap(bmp, srcRect, RectF(0f, 0f, dstW.toFloat(), dstH.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
    }
}
