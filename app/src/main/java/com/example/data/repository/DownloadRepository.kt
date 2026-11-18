package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.model.ExportOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class DownloadRepository(
    private val context: Context,
    private val dao: DownloadDao
) {
    private val thumbDir: File
        get() = File(context.filesDir, "download_thumbs").apply { mkdirs() }

    val allDownloads: Flow<List<DownloadEntity>> = dao.observeAll()

    suspend fun recordDownload(
        fileName: String,
        option: ExportOption,
        format: String,
        sizeBytes: Long,
        width: Int,
        height: Int,
        uriString: String,
        previewBitmap: Bitmap,
        projectId: String?
    ): DownloadEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val thumbFile = File(thumbDir, "$id.jpg")
        val scaled = scaleThumb(previewBitmap, 300)
        FileOutputStream(thumbFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        if (scaled !== previewBitmap) scaled.recycle()

        val entity = DownloadEntity(
            id = id,
            fileName = fileName,
            optionType = option.name,
            format = format,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            uriString = uriString,
            thumbPath = thumbFile.absolutePath,
            projectId = projectId,
            createdAt = System.currentTimeMillis()
        )
        dao.insert(entity)
        entity
    }

    suspend fun renameDownload(item: DownloadEntity, newFileName: String): Boolean = withContext(Dispatchers.IO) {
        val cleanName = newFileName.trim()
        if (cleanName.isEmpty()) return@withContext false
        val uri = Uri.parse(item.uriString)
        var updatedUriString = item.uriString
        runCatching {
            if (uri.scheme == "content") {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, cleanName)
                }
                val rows = context.contentResolver.update(uri, values, null, null)
                if (rows == 0) {
                    DocumentsContract.renameDocument(context.contentResolver, uri, cleanName)?.let {
                        updatedUriString = it.toString()
                    }
                }
            } else if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                val dst = File(f.parentFile, cleanName)
                if (f.renameTo(dst)) updatedUriString = Uri.fromFile(dst).toString()
            }
        }
        dao.updateFile(item.id, cleanName, updatedUriString)
        true
    }

    suspend fun deleteDownload(item: DownloadEntity) = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(item.uriString)
            if (uri.scheme == "content") {
                context.contentResolver.delete(uri, null, null)
            } else if (uri.scheme == "file") {
                File(uri.path ?: "").delete()
            }
        }
        File(item.thumbPath).delete()
        dao.deleteById(item.id)
    }

    private fun scaleThumb(bmp: Bitmap, maxDim: Int): Bitmap {
        val maxSide = maxOf(bmp.width, bmp.height)
        if (maxSide <= maxDim) return bmp
        val ratio = maxDim.toFloat() / maxSide
        return Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * ratio).toInt().coerceAtLeast(1),
            (bmp.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
