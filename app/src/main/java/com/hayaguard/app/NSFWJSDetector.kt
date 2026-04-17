package com.hayaguard.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NSFWJSDetector(context: Context) {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var isInitialized = false
    private val inputSize = 224
    private val numClasses = 5
    private val drawingThreshold = 0.55f
    private val hentaiThreshold = 0.55f
    private val pornThreshold = 0.85f
    private val sexyThreshold = 0.80f

    data class DetectionResult(val isNSFW: Boolean, val confidence: Float, val category: String)

    init {
        try {
            val result = TFLiteInterpreterFactory.createInterpreter(context, "nsfw_mobilenet.tflite")
            interpreter = result.interpreter
            gpuDelegate = result.gpuDelegate
            isInitialized = true
            Log.d("NSFWJSDetector", "Model loaded with ${result.delegateType}")
        } catch (e: Exception) {
            Log.e("NSFWJSDetector", "Failed to load model: ${e.message}", e)
            isInitialized = false
        }
    }

    fun detectNSFW(bitmap: Bitmap): DetectionResult {
        if (!isInitialized || interpreter == null) {
            return DetectionResult(false, 0f, "")
        }

        return try {
            val needsScale = bitmap.width != inputSize || bitmap.height != inputSize
            val inputBitmap = if (needsScale) {
                BitmapPool.scaleTo224(bitmap)
            } else {
                bitmap
            }

            val inputBuffer = prepareInput(inputBitmap)
            val output = Array(1) { FloatArray(numClasses) }

            try {
                interpreter!!.run(inputBuffer, output)
            } catch (e: Exception) {
                Log.e("NSFWJSDetector", "Inference failed, triggering GPU fallback: ${e.message}")
                TFLiteInterpreterFactory.markGpuCrashed()
                if (needsScale && inputBitmap != bitmap) {
                    BitmapPool.release(inputBitmap)
                }
                return DetectionResult(false, 0f, "")
            }

            if (needsScale && inputBitmap != bitmap) {
                BitmapPool.release(inputBitmap)
            }

            val scores = output[0]
            val drawingScore = scores[0]
            val hentaiScore = scores[1]
            val pornScore = scores[3]
            val sexyScore = scores[4]

            var maxConf = 0f
            var category = ""
            var isNSFW = false

            if (hentaiScore > hentaiThreshold && hentaiScore > maxConf) {
                maxConf = hentaiScore
                category = "hentai"
                isNSFW = true
            }
            if (pornScore > pornThreshold && pornScore > maxConf) {
                maxConf = pornScore
                category = "porn"
                isNSFW = true
            }
            if (sexyScore > sexyThreshold && sexyScore > maxConf) {
                maxConf = sexyScore
                category = "sexy"
                isNSFW = true
            }
            if (drawingScore > drawingThreshold && hentaiScore > 0.20f) {
                val combinedScore = (drawingScore * 0.4f + hentaiScore * 0.6f)
                if (combinedScore > 0.45f && combinedScore > maxConf) {
                    maxConf = combinedScore
                    category = "drawing"
                    isNSFW = true
                }
            }
            if (drawingScore > 0.40f && hentaiScore > 0.40f) {
                val combinedScore = (drawingScore + hentaiScore) / 2
                if (!isNSFW || combinedScore > maxConf) {
                    maxConf = combinedScore
                    category = "animated"
                    isNSFW = true
                }
            }

            DetectionResult(isNSFW, maxConf, category)
        } catch (e: Exception) {
            DetectionResult(false, 0f, "")
        }
    }

    private fun prepareInput(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }

        byteBuffer.rewind()
        return byteBuffer
    }

    fun close() {
        try {
            interpreter?.close()
            gpuDelegate?.close()
        } catch (e: Exception) {
            Log.e("NSFWJSDetector", "Error closing: ${e.message}")
        }
    }
}
