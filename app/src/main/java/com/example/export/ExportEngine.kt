package com.example.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.example.data.model.ExportConfig
import com.example.data.model.ExportFormat
import com.example.data.model.ExportOption
import com.example.data.model.ExportResultInfo
import com.example.data.model.ExportSizePreset
import com.example.data.model.SavedOutputFile
import com.example.data.repository.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

sealed class ExportCheckResult {
    data object Ok : ExportCheckResult()
    data class NameCollision(val existingName: String) : ExportCheckResult()
    data class ErrorHinglish(val message: String) : ExportCheckResult()
}

class ExportEngine(
    private val context: Context,
    private val downloadRepository: DownloadRepository
) {
    fun hasEnoughStorage(minRequiredBytes: Long = 12L * 1024L * 1024L): Boolean {
        return runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes >= minRequiredBytes
        }.getOrDefault(true)
    }

    fun checkExportPreconditions(config: ExportConfig): ExportCheckResult {
        if (!hasEnoughStorage()) {
            return ExportCheckResult.ErrorHinglish("Phone mein jagah kam hai — kripya thoda storage khali karein.")
        }
        val base = sanitizeName(config.fileName)
        val ext = effectiveExtension(config.option, config.format, config.saveAsZipForBoth)
        val candidateName = "$base${suffixFor(config.option)}.$ext"

        if (config.useCustomFolder && !config.customFolderUri.isNullOrBlank()) {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(config.customFolderUri))
            if (tree == null || !tree.canWrite()) {
                return ExportCheckResult.ErrorHinglish("Chune hue folder mein save karne ki permission nahi mili. Dobara folder chunein.")
            }
            if (tree.findFile(candidateName) != null) {
                return ExportCheckResult.NameCollision(candidateName)
            }
        } else {
            val exists = queryMediaStoreExists(candidateName)
            if (exists) return ExportCheckResult.NameCollision(candidateName)
        }
        return ExportCheckResult.Ok
    }

    suspend fun performExport(
        config: ExportConfig,
        cutoutFull: Bitmap?,
        cleanBgFull: Bitmap?,
        combinedFull: Bitmap?,
        projectId: String?,
        overwriteExisting: Boolean,
        autoNumberOnConflict: Boolean,
        onProgress: (Float, String) -> Unit
    ): Result<ExportResultInfo> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasEnoughStorage()) {
                throw IllegalStateException("Phone mein jagah kam hai — export nahi ho saka.")
            }

            val baseName = sanitizeName(config.fileName)
            var note: String? = null
            if (config.format == ExportFormat.JPG && (config.option == ExportOption.BG_REMOVE || config.option == ExportOption.BOTH)) {
                note = "Note: JPG mein transparency nahi hoti, isliye cutout PNG mein save kiya gaya hai."
            }

            val savedList = mutableListOf<SavedOutputFile>()
            var totalBytes = 0L

            when (config.option) {
                ExportOption.BG_REMOVE -> {
                    val raw = cutoutFull ?: throw IllegalStateException("Cutout taiyaar nahi ho saka.")
                    onProgress(0.35f, "Cutout size aur quality set ho rahi hai...")
                    val prepared = prepareBitmapForExport(raw, config, forceOpaqueBg = false)
                    val fmt = if (config.format == ExportFormat.JPG) ExportFormat.PNG else config.format
                    val name = resolveFinalName("${baseName}_cutout.${fmt.ext}", config, overwriteExisting, autoNumberOnConflict)
                    onProgress(0.75f, "Gallery mein save ho raha hai...")
                    val saved = writeImageToDestination(prepared, fmt, config.quality, name, config, ExportOption.BG_REMOVE)
                    downloadRepository.recordDownload(saved.displayName, ExportOption.BG_REMOVE, fmt.label, saved.sizeBytes, prepared.width, prepared.height, saved.uriString, prepared, projectId)
                    if (prepared !== raw) prepared.recycle()
                    savedList += saved
                    totalBytes += saved.sizeBytes
                }

                ExportOption.OBJECT_REMOVE -> {
                    val raw = cleanBgFull ?: throw IllegalStateException("Saaf background taiyaar nahi ho saka.")
                    onProgress(0.35f, "Saaf background prepare ho raha hai...")
                    val prepared = prepareBitmapForExport(raw, config, forceOpaqueBg = config.format == ExportFormat.JPG)
                    val fmt = config.format
                    val name = resolveFinalName("${baseName}_clean_bg.${fmt.ext}", config, overwriteExisting, autoNumberOnConflict)
                    onProgress(0.75f, "Save ho raha hai...")
                    val saved = writeImageToDestination(prepared, fmt, config.quality, name, config, ExportOption.OBJECT_REMOVE)
                    downloadRepository.recordDownload(saved.displayName, ExportOption.OBJECT_REMOVE, fmt.label, saved.sizeBytes, prepared.width, prepared.height, saved.uriString, prepared, projectId)
                    if (prepared !== raw) prepared.recycle()
                    savedList += saved
                    totalBytes += saved.sizeBytes
                }

                ExportOption.BOTH -> {
                    val cut = cutoutFull ?: throw IllegalStateException("Cutout taiyaar nahi hai.")
                    val clean = cleanBgFull ?: throw IllegalStateException("Saaf background taiyaar nahi hai.")
                    onProgress(0.25f, "Dono results prepare ho rahe hain...")

                    val cutPrep = prepareBitmapForExport(cut, config, forceOpaqueBg = false)
                    val cleanPrep = prepareBitmapForExport(clean, config, forceOpaqueBg = config.format == ExportFormat.JPG)
                    val combPrep = combinedFull?.let { prepareBitmapForExport(it, config, forceOpaqueBg = config.format == ExportFormat.JPG) }

                    val cutFmt = if (config.format == ExportFormat.JPG) ExportFormat.PNG else config.format
                    val cleanFmt = config.format

                    if (config.saveAsZipForBoth) {
                        onProgress(0.65f, "ZIP file ban rahi hai...")
                        val zipName = resolveFinalName("${baseName}_both.zip", config, overwriteExisting, autoNumberOnConflict)
                        val zipSaved = writeBothZipToDestination(
                            zipName = zipName,
                            baseName = baseName,
                            cutout = cutPrep,
                            cutFmt = cutFmt,
                            cleanBg = cleanPrep,
                            cleanFmt = cleanFmt,
                            combined = combPrep,
                            quality = config.quality,
                            config = config
                        )
                        downloadRepository.recordDownload(zipSaved.displayName, ExportOption.BOTH, "ZIP", zipSaved.sizeBytes, cutPrep.width, cutPrep.height, zipSaved.uriString, combPrep ?: cutPrep, projectId)
                        savedList += zipSaved
                        totalBytes += zipSaved.sizeBytes
                    } else {
                        onProgress(0.55f, "Cutout save ho raha hai...")
                        val cutName = resolveFinalName("${baseName}_cutout.${cutFmt.ext}", config, overwriteExisting, autoNumberOnConflict)
                        val savedCut = writeImageToDestination(cutPrep, cutFmt, config.quality, cutName, config, ExportOption.BG_REMOVE)
                        downloadRepository.recordDownload(savedCut.displayName, ExportOption.BG_REMOVE, cutFmt.label, savedCut.sizeBytes, cutPrep.width, cutPrep.height, savedCut.uriString, cutPrep, projectId)
                        savedList += savedCut
                        totalBytes += savedCut.sizeBytes

                        onProgress(0.75f, "Clean background save ho raha hai...")
                        val cleanName = resolveFinalName("${baseName}_clean_bg.${cleanFmt.ext}", config, overwriteExisting, autoNumberOnConflict)
                        val savedClean = writeImageToDestination(cleanPrep, cleanFmt, config.quality, cleanName, config, ExportOption.OBJECT_REMOVE)
                        downloadRepository.recordDownload(savedClean.displayName, ExportOption.OBJECT_REMOVE, cleanFmt.label, savedClean.sizeBytes, cleanPrep.width, cleanPrep.height, savedClean.uriString, cleanPrep, projectId)
                        savedList += savedClean
                        totalBytes += savedClean.sizeBytes

                        if (combPrep != null) {
                            val combName = resolveFinalName("${baseName}_combined.${cleanFmt.ext}", config, overwriteExisting, autoNumberOnConflict)
                            val savedComb = writeImageToDestination(combPrep, cleanFmt, config.quality, combName, config, ExportOption.BOTH)
                            downloadRepository.recordDownload(savedComb.displayName, ExportOption.BOTH, cleanFmt.label, savedComb.sizeBytes, combPrep.width, combPrep.height, savedComb.uriString, combPrep, projectId)
                            savedList += savedComb
                            totalBytes += savedComb.sizeBytes
                        }
                    }

                    if (cutPrep !== cut) cutPrep.recycle()
                    if (cleanPrep !== clean) cleanPrep.recycle()
                    if (combPrep != null && combPrep !== combinedFull) combPrep.recycle()
                }
            }
            onProgress(1f, "Export complete!")
            ExportResultInfo(savedList, totalBytes, note)
        }
    }

    suspend fun exportWhatsAppSticker(
        cutout: Bitmap,
        name: String,
        outlineColorArgb: Int = Color.WHITE,
        projectId: String?
    ): Result<SavedOutputFile> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = BitmapUtils.trimTransparentBounds(cutout)
            val outlined = BitmapUtils.addStickerOutline(trimmed, 14f, outlineColorArgb)
            if (trimmed !== cutout) trimmed.recycle()

            val canvasBmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val c = Canvas(canvasBmp)
            val pad = 24f
            val scale = min((512f - pad * 2) / outlined.width, (512f - pad * 2) / outlined.height)
            val dw = outlined.width * scale
            val dh = outlined.height * scale
            val dst = RectF((512f - dw) / 2f, (512f - dh) / 2f, (512f + dw) / 2f, (512f + dh) / 2f)
            c.drawBitmap(outlined, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            outlined.recycle()

            val fileName = "${sanitizeName(name)}_wa_sticker.webp"
            val saved = writeImageToDestination(canvasBmp, ExportFormat.WEBP, 84, fileName, ExportConfig(), ExportOption.BG_REMOVE)
            downloadRepository.recordDownload(saved.displayName, ExportOption.BG_REMOVE, "WA Sticker", saved.sizeBytes, 512, 512, saved.uriString, canvasBmp, projectId)
            canvasBmp.recycle()
            saved
        }
    }

    suspend fun exportStickerPackZip(
        stickers: List<Bitmap>,
        packName: String,
        outlineColorArgb: Int = Color.WHITE
    ): Result<SavedOutputFile> = withContext(Dispatchers.IO) {
        runCatching {
            val zipName = "${sanitizeName(packName)}_sticker_pack.zip"
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                stickers.forEachIndexed { index, raw ->
                    val trimmed = BitmapUtils.trimTransparentBounds(raw)
                    val outlined = BitmapUtils.addStickerOutline(trimmed, 12f, outlineColorArgb)
                    if (trimmed !== raw) trimmed.recycle()

                    val st = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                    val c = Canvas(st)
                    val scale = min(464f / outlined.width, 464f / outlined.height)
                    val dw = outlined.width * scale
                    val dh = outlined.height * scale
                    c.drawBitmap(outlined, null, RectF((512f - dw) / 2f, (512f - dh) / 2f, (512f + dw) / 2f, (512f + dh) / 2f), Paint(Paint.FILTER_BITMAP_FLAG))
                    outlined.recycle()

                    val bytes = encodeBitmapBytes(st, ExportFormat.WEBP, 82)
                    st.recycle()
                    zos.putNextEntry(ZipEntry("sticker_${index + 1}.webp"))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
            val zipBytes = baos.toByteArray()
            val saved = writeRawFileToMediaStore(zipName, "application/zip", zipBytes, ExportOption.BG_REMOVE)
            stickers.firstOrNull()?.let { first ->
                downloadRepository.recordDownload(saved.displayName, ExportOption.BG_REMOVE, "Sticker Pack", saved.sizeBytes, 512, 512, saved.uriString, first, null)
            }
            saved
        }
    }

    suspend fun exportPassportSheet(
        cutoutOrComposite: Bitmap,
        name: String,
        asPdf: Boolean,
        copiesCount: Int = 8,
        bgColorArgb: Int = Color.WHITE,
        projectId: String?
    ): Result<SavedOutputFile> = withContext(Dispatchers.IO) {
        runCatching {
            // A4 at 150 DPI = 1240 x 1754
            val pageW = 1240
            val pageH = 1754
            val sheet = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.WHITE)

            // Passport photo box ~ 35mm x 45mm at 150 DPI -> 207 x 266 px
            val cardW = 240
            val cardH = 308
            val photoBmp = Bitmap.createBitmap(cardW, cardH, Bitmap.Config.ARGB_8888)
            val pc = Canvas(photoBmp)
            pc.drawColor(bgColorArgb)
            val scale = max(cardW.toFloat() / cutoutOrComposite.width, cardH.toFloat() / cutoutOrComposite.height)
            val dw = cutoutOrComposite.width * scale
            val dh = cutoutOrComposite.height * scale
            pc.drawBitmap(
                cutoutOrComposite,
                null,
                RectF((cardW - dw) / 2f, (cardH - dh) / 2f, (cardW + dw) / 2f, (cardH + dh) / 2f),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
            val borderPaint = Paint().apply {
                color = Color.LTGRAY
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }

            val cols = 4
            val gap = 36
            val startX = (pageW - (cols * cardW + (cols - 1) * gap)) / 2
            val startY = 90
            for (i in 0 until copiesCount.coerceIn(1, 16)) {
                val col = i % cols
                val row = i / cols
                val x = startX + col * (cardW + gap)
                val y = startY + row * (cardH + gap)
                canvas.drawBitmap(photoBmp, x.toFloat(), y.toFloat(), null)
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + cardW).toFloat(), (y + cardH).toFloat(), borderPaint)
            }
            photoBmp.recycle()

            val cleanName = sanitizeName(name)
            val saved = if (asPdf) {
                val doc = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
                val page = doc.startPage(pageInfo)
                page.canvas.drawBitmap(sheet, 0f, 0f, null)
                doc.finishPage(page)
                val pdfBytes = ByteArrayOutputStream().use {
                    doc.writeTo(it)
                    doc.close()
                    it.toByteArray()
                }
                writeRawFileToMediaStore("${cleanName}_passport.pdf", "application/pdf", pdfBytes, ExportOption.BG_REMOVE)
            } else {
                writeImageToDestination(sheet, ExportFormat.JPG, 95, "${cleanName}_passport_sheet.jpg", ExportConfig(), ExportOption.BG_REMOVE)
            }
            downloadRepository.recordDownload(saved.displayName, ExportOption.BG_REMOVE, if (asPdf) "PDF" else "JPG", saved.sizeBytes, pageW, pageH, saved.uriString, sheet, projectId)
            sheet.recycle()
            saved
        }
    }

    suspend fun exportPrintReadyA4(
        bitmap: Bitmap,
        name: String,
        projectId: String?
    ): Result<SavedOutputFile> = withContext(Dispatchers.IO) {
        runCatching {
            val pageW = 1240
            val pageH = 1754
            val sheet = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.WHITE)
            val margin = 72f
            val scale = min((pageW - margin * 2) / bitmap.width, (pageH - margin * 2) / bitmap.height)
            val dw = bitmap.width * scale
            val dh = bitmap.height * scale
            canvas.drawBitmap(
                bitmap,
                null,
                RectF((pageW - dw) / 2f, (pageH - dh) / 2f, (pageW + dw) / 2f, (pageH + dh) / 2f),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
            val fileName = "${sanitizeName(name)}_print_A4.jpg"
            val saved = writeImageToDestination(sheet, ExportFormat.JPG, 95, fileName, ExportConfig(), ExportOption.BG_REMOVE)
            downloadRepository.recordDownload(saved.displayName, ExportOption.BG_REMOVE, "A4 Print", saved.sizeBytes, pageW, pageH, saved.uriString, sheet, projectId)
            sheet.recycle()
            saved
        }
    }

    private fun prepareBitmapForExport(raw: Bitmap, config: ExportConfig, forceOpaqueBg: Boolean): Bitmap {
        var current = if (config.trimTransparent && !forceOpaqueBg) {
            BitmapUtils.trimTransparentBounds(raw)
        } else raw

        val (targetW, targetH) = when (config.sizePreset) {
            ExportSizePreset.ORIGINAL -> current.width to current.height
            ExportSizePreset.CUSTOM -> config.customWidth.coerceIn(128, 4096) to config.customHeight.coerceIn(128, 4096)
            else -> {
                val maxD = config.sizePreset.maxDim ?: max(current.width, current.height)
                val r = min(1f, maxD.toFloat() / max(current.width, current.height))
                (current.width * r).roundToInt().coerceAtLeast(1) to (current.height * r).roundToInt().coerceAtLeast(1)
            }
        }

        if (targetW != current.width || targetH != current.height) {
            val scaled = Bitmap.createScaledBitmap(current, targetW, targetH, true)
            if (current !== raw) current.recycle()
            current = scaled
        }

        if (forceOpaqueBg) {
            val opaque = Bitmap.createBitmap(current.width, current.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(opaque)
            c.drawColor(config.jpgFillColorArgb)
            c.drawBitmap(current, 0f, 0f, null)
            if (current !== raw) current.recycle()
            current = opaque
        }

        return current
    }

    private fun writeImageToDestination(
        bmp: Bitmap,
        format: ExportFormat,
        quality: Int,
        fileName: String,
        config: ExportConfig,
        option: ExportOption
    ): SavedOutputFile {
        val bytes = encodeBitmapBytes(bmp, format, quality)
        val mime = when (format) {
            ExportFormat.PNG -> "image/png"
            ExportFormat.WEBP -> "image/webp"
            ExportFormat.JPG -> "image/jpeg"
        }
        return if (config.useCustomFolder && !config.customFolderUri.isNullOrBlank()) {
            writeBytesToSafFolder(Uri.parse(config.customFolderUri), fileName, mime, bytes, option)
        } else {
            writeRawFileToMediaStore(fileName, mime, bytes, option)
        }
    }

    private fun writeBothZipToDestination(
        zipName: String,
        baseName: String,
        cutout: Bitmap,
        cutFmt: ExportFormat,
        cleanBg: Bitmap,
        cleanFmt: ExportFormat,
        combined: Bitmap?,
        quality: Int,
        config: ExportConfig
    ): SavedOutputFile {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("${baseName}_cutout.${cutFmt.ext}"))
            zos.write(encodeBitmapBytes(cutout, cutFmt, quality))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("${baseName}_clean_bg.${cleanFmt.ext}"))
            zos.write(encodeBitmapBytes(cleanBg, cleanFmt, quality))
            zos.closeEntry()

            if (combined != null) {
                zos.putNextEntry(ZipEntry("${baseName}_combined.${cleanFmt.ext}"))
                zos.write(encodeBitmapBytes(combined, cleanFmt, quality))
                zos.closeEntry()
            }
        }
        val bytes = baos.toByteArray()
        return if (config.useCustomFolder && !config.customFolderUri.isNullOrBlank()) {
            writeBytesToSafFolder(Uri.parse(config.customFolderUri), zipName, "application/zip", bytes, ExportOption.BOTH)
        } else {
            writeRawFileToMediaStore(zipName, "application/zip", bytes, ExportOption.BOTH)
        }
    }

    private fun encodeBitmapBytes(bmp: Bitmap, format: ExportFormat, quality: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        val compressFmt = when (format) {
            ExportFormat.PNG -> Bitmap.CompressFormat.PNG
            ExportFormat.JPG -> Bitmap.CompressFormat.JPEG
            ExportFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (quality >= 99) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        }
        bmp.compress(compressFmt, quality.coerceIn(10, 100), baos)
        return baos.toByteArray()
    }

    private fun writeBytesToSafFolder(
        treeUri: Uri,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        option: ExportOption
    ): SavedOutputFile {
        val dir = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("Chuna hua folder open nahi ho saka.")
        dir.findFile(fileName)?.delete()
        val doc = dir.createFile(mimeType, fileName)
            ?: throw IllegalStateException("Folder mein file create nahi ho saki.")
        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: throw IllegalStateException("File likhne mein dikkat aayi.")
        return SavedOutputFile(fileName, doc.uri.toString(), mimeType, bytes.size.toLong(), option)
    }

    private fun writeRawFileToMediaStore(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        option: ExportOption
    ): SavedOutputFile {
        val isImage = mimeType.startsWith("image/")
        val relPath = if (isImage) {
            "${Environment.DIRECTORY_PICTURES}/Cutout Pro"
        } else {
            "${Environment.DIRECTORY_DOWNLOADS}/Cutout Pro"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (isImage) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(collection, values)
                ?: throw IllegalStateException("Gallery mein jagah banane mein error aaya.")
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                    out.flush()
                } ?: throw IllegalStateException("File write nahi ho saki.")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                return SavedOutputFile(fileName, uri.toString(), mimeType, bytes.size.toLong(), option)
            } catch (e: Exception) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw e
            }
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Cutout Pro").apply { mkdirs() }
            val f = File(dir, fileName)
            FileOutputStream(f).use { it.write(bytes) }
            return SavedOutputFile(fileName, Uri.fromFile(f).toString(), mimeType, bytes.size.toLong(), option)
        }
    }

    private fun resolveFinalName(
        requested: String,
        config: ExportConfig,
        overwriteExisting: Boolean,
        autoNumberOnConflict: Boolean
    ): String {
        if (overwriteExisting) {
            deleteExistingIfFound(requested, config)
            return requested
        }
        if (!autoNumberOnConflict) return requested

        val dot = requested.lastIndexOf('.')
        val stem = if (dot > 0) requested.substring(0, dot) else requested
        val ext = if (dot > 0) requested.substring(dot) else ""
        var idx = 1
        var candidate = requested
        while (fileExistsAtDestination(candidate, config) && idx < 999) {
            candidate = "${stem}_$idx$ext"
            idx++
        }
        return candidate
    }

    private fun fileExistsAtDestination(name: String, config: ExportConfig): Boolean {
        return if (config.useCustomFolder && !config.customFolderUri.isNullOrBlank()) {
            DocumentFile.fromTreeUri(context, Uri.parse(config.customFolderUri))?.findFile(name) != null
        } else {
            queryMediaStoreExists(name)
        }
    }

    private fun deleteExistingIfFound(name: String, config: ExportConfig) {
        if (config.useCustomFolder && !config.customFolderUri.isNullOrBlank()) {
            DocumentFile.fromTreeUri(context, Uri.parse(config.customFolderUri))?.findFile(name)?.delete()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cols = arrayOf(MediaStore.MediaColumns._ID)
            val sel = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols, sel, arrayOf(name), null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
            }
        }
    }

    private fun queryMediaStoreExists(fileName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val cols = arrayOf(MediaStore.MediaColumns._ID)
        val sel = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols, sel, arrayOf(fileName), null)?.use {
            return it.count > 0
        }
        return false
    }

    private fun sanitizeName(name: String): String {
        return name.trim().replace(Regex("[^A-Za-z0-9_\\-]"), "_").ifEmpty { "cutout_pro" }
    }

    private fun effectiveExtension(option: ExportOption, fmt: ExportFormat, zipForBoth: Boolean): String {
        return when (option) {
            ExportOption.BOTH -> if (zipForBoth) "zip" else fmt.ext
            ExportOption.BG_REMOVE -> if (fmt == ExportFormat.JPG) "png" else fmt.ext
            ExportOption.OBJECT_REMOVE -> fmt.ext
        }
    }

    private fun suffixFor(option: ExportOption): String = when (option) {
        ExportOption.BG_REMOVE -> "_cutout"
        ExportOption.OBJECT_REMOVE -> "_clean_bg"
        ExportOption.BOTH -> "_both"
    }

    fun showBatchProgressNotification(done: Int, total: Int, finished: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "cutout_pro_batch"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Batch Export", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Cutout Pro Batch Export")
            .setContentText(
                if (finished) "$done / $total photos save ho chuki hain!"
                else "Process ho raha hai: $done / $total"
            )
            .setOngoing(!finished)
            .setProgress(total, done, false)
        runCatching { nm.notify(1042, builder.build()) }
    }
}
