package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.LiteRtException
import com.google.ai.edge.litert.TensorBuffer
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Thread-confined LiteRT 2.3.0 inference wrapper following official LiteRT runtime mandates:
 * - Single-thread executor owns CompiledModel creation, inference, and closing
 * - TensorBuffers are allocated once and reused across calls
 * - Warm-up inference runs in init; GPU falls back cleanly to CPU on LiteRtException
 */
class LiteRtSession(
    context: Context,
    private val assetPath: String,
    private val inputFloatCount: Int,
    accelerator: Accelerator
) : AutoCloseable {

    private val model: CompiledModel = CompiledModel.create(
        context.assets,
        assetPath,
        if (accelerator == Accelerator.GPU) {
            CompiledModel.Options(Accelerator.GPU, Accelerator.CPU)
        } else {
            CompiledModel.Options(Accelerator.CPU)
        },
        sharedEnvironment
    )

    private val inputs = mutableListOf<TensorBuffer>()
    private val outputs = mutableListOf<TensorBuffer>()

    init {
        try {
            inputs += model.createInputBuffers()
            outputs += model.createOutputBuffers()
            infer(FloatArray(inputFloatCount))
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    fun infer(input: FloatArray): FloatArray {
        inputs[0].writeFloat(input)
        model.run(inputs, outputs)
        return outputs[0].readFloat()
    }

    override fun close() {
        (inputs + outputs).forEach { runCatching { it.close() } }
        inputs.clear()
        outputs.clear()
        runCatching { model.close() }
    }

    companion object {
        private val sharedEnvironment by lazy { Environment.create() }
    }
}

class LiteRtModelPool(private val context: Context) : AutoCloseable {
    private val inferenceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "CutoutPro-LiteRT").apply { isDaemon = true }
    }
    val dispatcher = inferenceExecutor.asCoroutineDispatcher()

    private val sessions = mutableMapOf<String, LiteRtSession>()

    suspend fun runModel(
        assetPath: String,
        inputWidth: Int,
        inputHeight: Int,
        channels: Int,
        inputFloats: FloatArray
    ): FloatArray = withContext(dispatcher) {
        val session = sessions.getOrPut(assetPath) {
            val count = inputWidth * inputHeight * channels
            try {
                LiteRtSession(context, assetPath, count, Accelerator.GPU)
            } catch (_: LiteRtException) {
                LiteRtSession(context, assetPath, count, Accelerator.CPU)
            } catch (_: Throwable) {
                LiteRtSession(context, assetPath, count, Accelerator.CPU)
            }
        }
        session.infer(inputFloats)
    }

    override fun close() {
        inferenceExecutor.execute {
            sessions.values.forEach { runCatching { it.close() } }
            sessions.clear()
        }
        inferenceExecutor.shutdown()
    }

    companion object {
        const val MODEL_SELFIE = "models/selfie_segmenter.tflite"
        const val MODEL_DEEPLAB = "models/deeplab_v3.tflite"
        const val MODEL_MULTICLASS = "models/selfie_multiclass_256x256.tflite"
        const val MODEL_HAIR = "models/hair_segmenter.tflite"

        fun bitmapToFloatRgb01(bitmap: Bitmap, targetW: Int, targetH: Int): FloatArray {
            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            val argb = if (scaled.config == Bitmap.Config.ARGB_8888) scaled else scaled.copy(Bitmap.Config.ARGB_8888, false)
            val pixels = IntArray(targetW * targetH)
            argb.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
            if (argb !== scaled) argb.recycle()
            if (scaled !== bitmap) scaled.recycle()

            val out = FloatArray(targetW * targetH * 3)
            for (i in pixels.indices) {
                val p = pixels[i]
                out[i * 3] = ((p ushr 16) and 0xFF) / 255f
                out[i * 3 + 1] = ((p ushr 8) and 0xFF) / 255f
                out[i * 3 + 2] = (p and 0xFF) / 255f
            }
            return out
        }

        fun bitmapToFloatRgbMinus1To1(bitmap: Bitmap, targetW: Int, targetH: Int): FloatArray {
            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            val argb = if (scaled.config == Bitmap.Config.ARGB_8888) scaled else scaled.copy(Bitmap.Config.ARGB_8888, false)
            val pixels = IntArray(targetW * targetH)
            argb.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
            if (argb !== scaled) argb.recycle()
            if (scaled !== bitmap) scaled.recycle()

            val out = FloatArray(targetW * targetH * 3)
            for (i in pixels.indices) {
                val p = pixels[i]
                out[i * 3] = (((p ushr 16) and 0xFF) - 127.5f) / 127.5f
                out[i * 3 + 1] = (((p ushr 8) and 0xFF) - 127.5f) / 127.5f
                out[i * 3 + 2] = ((p and 0xFF) - 127.5f) / 127.5f
            }
            return out
        }

        fun bitmapToFloatRgba01(bitmap: Bitmap, targetW: Int, targetH: Int): FloatArray {
            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            val argb = if (scaled.config == Bitmap.Config.ARGB_8888) scaled else scaled.copy(Bitmap.Config.ARGB_8888, false)
            val pixels = IntArray(targetW * targetH)
            argb.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
            if (argb !== scaled) argb.recycle()
            if (scaled !== bitmap) scaled.recycle()

            val out = FloatArray(targetW * targetH * 4)
            for (i in pixels.indices) {
                val p = pixels[i]
                out[i * 4] = ((p ushr 16) and 0xFF) / 255f
                out[i * 4 + 1] = ((p ushr 8) and 0xFF) / 255f
                out[i * 4 + 2] = (p and 0xFF) / 255f
                out[i * 4 + 3] = 0f
            }
            return out
        }
    }
}
