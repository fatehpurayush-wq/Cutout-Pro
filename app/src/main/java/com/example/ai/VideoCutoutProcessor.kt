package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import com.example.data.model.BackgroundSpec
import com.example.data.model.QualityMode
import com.example.data.model.SubjectType
import com.example.export.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.min

enum class VideoExportFormat(val label: String, val ext: String) {
    MP4("MP4 Video", "mp4"),
    ANIMATED_WEBP("Transparent Animated WEBP", "webp")
}

data class VideoMetadataInfo(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val previewFrame: Bitmap?
)

class VideoCutoutProcessor(
    private val context: Context,
    private val segmentationEngine: OnDeviceSegmentationEngine
) {
    suspend fun inspectVideo(uri: Uri): VideoMetadataInfo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280
            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            VideoMetadataInfo(dur, w, h, frame)
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun renderPreviewFrame(
        frame: Bitmap,
        bgSpec: BackgroundSpec
    ): Bitmap = withContext(Dispatchers.Default) {
        val seg = segmentationEngine.detectSubjectMask(frame, SubjectType.AUTO, hairDetail = false, quality = QualityMode.FAST)
        val cutout = segmentationEngine.applyCutoutWithRefinement(frame, seg.mask, com.example.data.model.EdgeSettings())
        seg.mask.recycle()
        val composed = BitmapUtils.composeCutoutWithBackground(context, frame, cutout, bgSpec)
        cutout.recycle()
        composed
    }

    suspend fun exportVideo(
        uri: Uri,
        bgSpec: BackgroundSpec,
        format: VideoExportFormat,
        outputFile: File,
        onProgress: (Float, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 3000L)
                .coerceAtMost(15_000L)
            val fps = if (format == VideoExportFormat.ANIMATED_WEBP) 8 else 12
            val totalFrames = ((durationMs / 1000f) * fps).toInt().coerceIn(1, 180)
            val stepUs = (durationMs * 1000L) / totalFrames

            val frames = ArrayList<Bitmap>(totalFrames)
            for (i in 0 until totalFrames) {
                if (!coroutineContext.isActive) break
                onProgress(0.05f + (i.toFloat() / totalFrames) * 0.7f, "Frame ${i + 1} / $totalFrames process ho raha hai...")
                val raw = retriever.getFrameAtTime(i * stepUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                val scaled = BitmapUtils.scaleDownToMax(raw, if (format == VideoExportFormat.ANIMATED_WEBP) 480 else 640)
                if (scaled !== raw) raw.recycle()
                val seg = segmentationEngine.detectSubjectMask(scaled, SubjectType.AUTO, hairDetail = false, quality = QualityMode.FAST)
                val cutout = segmentationEngine.applyCutoutWithRefinement(scaled, seg.mask, com.example.data.model.EdgeSettings())
                seg.mask.recycle()
                val outFrame = if (format == VideoExportFormat.ANIMATED_WEBP && bgSpec.type == com.example.data.model.BackgroundType.TRANSPARENT) {
                    cutout
                } else {
                    BitmapUtils.composeCutoutWithBackground(context, scaled, cutout, bgSpec).also {
                        cutout.recycle()
                    }
                }
                scaled.recycle()
                frames.add(outFrame)
            }

            if (frames.isEmpty()) throw IllegalStateException("Video frames read nahi ho sake")

            onProgress(0.82f, "Final video file ban rahi hai...")
            if (format == VideoExportFormat.ANIMATED_WEBP) {
                writeAnimatedWebp(frames, (1000 / fps).coerceAtLeast(50), outputFile)
            } else {
                encodeMp4FromBitmaps(frames, fps, outputFile)
            }
            frames.forEach { it.recycle() }
            onProgress(1f, "Video export poora hua!")
            outputFile
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Pure-Kotlin RIFF Animated WebP muxer supporting alpha transparency (VP8X + ANIM + ANMF).
     */
    private fun writeAnimatedWebp(frames: List<Bitmap>, frameDurationMs: Int, outputFile: File) {
        val w = frames.first().width
        val h = frames.first().height
        val chunks = ByteArrayOutputStream()

        // VP8X chunk (10 bytes payload): flags(4) + canvasWidthMinus1(3) + canvasHeightMinus1(3)
        val vp8x = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        vp8x.putInt(0x00000012) // Animation (bit 1 = 0x02) + Alpha (bit 4 = 0x10)
        put24BitLE(vp8x, w - 1)
        put24BitLE(vp8x, h - 1)
        writeChunk(chunks, "VP8X", vp8x.array())

        // ANIM chunk (6 bytes payload): bgColor(4) + loopCount(2)
        val anim = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
        anim.putInt(0x00000000) // transparent background
        anim.putShort(0) // infinite loop
        writeChunk(chunks, "ANIM", anim.array())

        val compressFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        for (frame in frames) {
            val single = ByteArrayOutputStream()
            frame.compress(compressFormat, 82, single)
            val webpBytes = single.toByteArray()
            val payloadChunks = extractWebpImageChunks(webpBytes)

            val anmfHeader = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            put24BitLE(anmfHeader, 0) // x/2
            put24BitLE(anmfHeader, 0) // y/2
            put24BitLE(anmfHeader, frame.width - 1)
            put24BitLE(anmfHeader, frame.height - 1)
            put24BitLE(anmfHeader, frameDurationMs)
            anmfHeader.put(0x02.toByte()) // dispose to background, alpha blending enabled

            val anmfPayload = ByteArrayOutputStream()
            anmfPayload.write(anmfHeader.array())
            anmfPayload.write(payloadChunks)
            writeChunk(chunks, "ANMF", anmfPayload.toByteArray())
        }

        val body = chunks.toByteArray()
        FileOutputStream(outputFile).use { fos ->
            val riffHeader = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            riffHeader.put("RIFF".toByteArray(Charsets.US_ASCII))
            riffHeader.putInt(4 + body.size)
            riffHeader.put("WEBP".toByteArray(Charsets.US_ASCII))
            fos.write(riffHeader.array())
            fos.write(body)
        }
    }

    private fun extractWebpImageChunks(webpBytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        if (webpBytes.size < 12) return webpBytes
        var pos = 12 // skip RIFF + size + WEBP
        while (pos + 8 <= webpBytes.size) {
            val fourCc = String(webpBytes, pos, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(webpBytes, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val padded = size + (size and 1)
            if (pos + 8 + size > webpBytes.size) break
            if (fourCc == "VP8 " || fourCc == "VP8L" || fourCc == "ALPH") {
                out.write(webpBytes, pos, 8 + min(padded, webpBytes.size - pos - 8))
                if ((size and 1) == 1 && pos + 8 + size == webpBytes.size) {
                    out.write(0)
                }
            }
            pos += 8 + padded
        }
        return out.toByteArray()
    }

    private fun writeChunk(out: ByteArrayOutputStream, fourCc: String, data: ByteArray) {
        out.write(fourCc.toByteArray(Charsets.US_ASCII))
        val len = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size).array()
        out.write(len)
        out.write(data)
        if ((data.size and 1) == 1) out.write(0)
    }

    private fun put24BitLE(buf: ByteBuffer, value: Int) {
        buf.put((value and 0xFF).toByte())
        buf.put(((value ushr 8) and 0xFF).toByte())
        buf.put(((value ushr 16) and 0xFF).toByte())
    }

    private fun encodeMp4FromBitmaps(frames: List<Bitmap>, fps: Int, outputFile: File) {
        // Ensure even dimensions for H.264 encoder
        val w = (frames.first().width / 2) * 2
        val h = (frames.first().height / 2) * 2
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            for (frame in frames) {
                val canvas = inputSurface.lockCanvas(null)
                canvas.drawColor(Color.BLACK)
                val dstRect = android.graphics.Rect(0, 0, w, h)
                canvas.drawBitmap(frame, null, dstRect, paint)
                inputSurface.unlockCanvasAndPost(canvas)
                drainEncoder(codec, muxer, bufferInfo, endOfStream = false, trackIndexHolder = { idx ->
                    if (trackIndex == -1 && idx >= 0) {
                        trackIndex = idx
                        muxerStarted = true
                    }
                }, getTrackIndex = { trackIndex }, isMuxerStarted = { muxerStarted })
            }
            codec.signalEndOfInputStream()
            drainEncoder(codec, muxer, bufferInfo, endOfStream = true, trackIndexHolder = { idx ->
                if (trackIndex == -1 && idx >= 0) {
                    trackIndex = idx
                    muxerStarted = true
                }
            }, getTrackIndex = { trackIndex }, isMuxerStarted = { muxerStarted })
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { inputSurface.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        info: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        trackIndexHolder: (Int) -> Unit,
        getTrackIndex: () -> Int,
        isMuxerStarted: () -> Boolean
    ) {
        val timeoutUs = if (endOfStream) 10_000L else 0L
        while (true) {
            val status = codec.dequeueOutputBuffer(info, timeoutUs)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!isMuxerStarted()) {
                    val idx = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    trackIndexHolder(idx)
                }
            } else if (status >= 0) {
                val encodedData: ByteBuffer? = codec.getOutputBuffer(status)
                if (encodedData != null && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0 && isMuxerStarted()) {
                    encodedData.position(info.offset)
                    encodedData.limit(info.offset + info.size)
                    muxer.writeSampleData(getTrackIndex(), encodedData, info)
                }
                codec.releaseOutputBuffer(status, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        }
    }
}
