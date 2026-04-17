package com.hayaguard.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

class HayaModeProcessor(context: Context) {

    companion object {
        private const val TAG = "HayaModeProcessor"
    }

    private val genderClassifier: GenderClassifier
    private val faceDetector: FaceDetector
    private val pixelSize = 24

    init {
        genderClassifier = GenderClassifier(context)
        
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.15f)
            .build()
        
        faceDetector = FaceDetection.getClient(options)
    }

    data class HayaResult(
        val processed: Boolean,
        val bitmap: Bitmap?,
        val facesBlurred: Int
    )

    suspend fun processImage(
        bitmap: Bitmap,
        userGender: String
    ): HayaResult {
        if (!SettingsManager.isHayaModeEnabled()) {
            return HayaResult(false, null, 0)
        }

        Log.d(TAG, "Processing image for Haya Mode. User gender: $userGender, Image size: ${bitmap.width}x${bitmap.height}")

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val faces: List<Face>
        
        try {
            faces = faceDetector.process(inputImage).await()
            Log.d(TAG, "ML Kit detected ${faces.size} faces")
        } catch (e: Exception) {
            Log.e(TAG, "Face detection failed: ${e.message}")
            return HayaResult(false, null, 0)
        }

        if (faces.isEmpty()) {
            return HayaResult(false, null, 0)
        }

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        var facesBlurred = 0

        for ((index, face) in faces.withIndex()) {
            val boundingBox = face.boundingBox
            
            val left = boundingBox.left.coerceIn(0, bitmap.width - 1)
            val top = boundingBox.top.coerceIn(0, bitmap.height - 1)
            val right = boundingBox.right.coerceIn(left + 1, bitmap.width)
            val bottom = boundingBox.bottom.coerceIn(top + 1, bitmap.height)
            
            val faceWidth = right - left
            val faceHeight = bottom - top
            
            // Skip faces smaller than 35px - these are usually profile pics/avatars
            // Profile pictures in feed headers are typically small and belong to the poster
            if (faceWidth < 35 || faceHeight < 35) {
                Log.d(TAG, "Face $index too small (likely profile pic): ${faceWidth}x${faceHeight}")
                continue
            }
            
            // Skip very small relative faces (less than 5% of image) - likely profile pics/avatars
            val faceAreaRatio = (faceWidth.toFloat() * faceHeight) / (bitmap.width.toFloat() * bitmap.height)
            if (faceAreaRatio < 0.01f) {
                Log.d(TAG, "Face $index too small relative to image (${(faceAreaRatio * 100).toInt()}%) - skipping")
                continue
            }

            try {
                val faceBitmap = Bitmap.createBitmap(bitmap, left, top, faceWidth, faceHeight)
                
                val genderResult = genderClassifier.classify(faceBitmap)
                faceBitmap.recycle()
                
                Log.d(TAG, "Face $index: Gender=${genderResult.gender}, Confidence=${genderResult.confidence}")
                
                val shouldBlur = when (userGender.uppercase()) {
                    "MALE" -> genderResult.gender == GenderClassifier.Gender.FEMALE
                    "FEMALE" -> genderResult.gender == GenderClassifier.Gender.MALE
                    else -> false
                }

                // Increased confidence threshold to 0.65 to reduce false positives
                if (shouldBlur && genderResult.confidence > 0.65f) {
                    Log.d(TAG, "Blurring face $index (opposite gender with high confidence)")
                    pixelateFaceRegion(mutableBitmap, canvas, Rect(left, top, right, bottom))
                    facesBlurred++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing face $index: ${e.message}")
            }
        }

        Log.d(TAG, "Total faces blurred: $facesBlurred")

        return if (facesBlurred > 0) {
            HayaResult(true, mutableBitmap, facesBlurred)
        } else {
            mutableBitmap.recycle()
            HayaResult(false, null, 0)
        }
    }

    private fun pixelateFaceRegion(bitmap: Bitmap, canvas: Canvas, faceRect: Rect) {
        val padding = ((faceRect.width() + faceRect.height()) / 2 * 0.15f).toInt()
        val expandedRect = Rect(
            (faceRect.left - padding).coerceIn(0, bitmap.width - 1),
            (faceRect.top - padding).coerceIn(0, bitmap.height - 1),
            (faceRect.right + padding).coerceIn(1, bitmap.width),
            (faceRect.bottom + padding).coerceIn(1, bitmap.height)
        )

        val faceWidth = expandedRect.width()
        val faceHeight = expandedRect.height()
        
        if (faceWidth <= 0 || faceHeight <= 0) return

        val smallW = maxOf(faceWidth / pixelSize, 1)
        val smallH = maxOf(faceHeight / pixelSize, 1)

        val faceBitmap = Bitmap.createBitmap(bitmap, expandedRect.left, expandedRect.top, faceWidth, faceHeight)
        val small = Bitmap.createScaledBitmap(faceBitmap, smallW, smallH, false)
        val pixelated = Bitmap.createScaledBitmap(small, faceWidth, faceHeight, false)
        
        canvas.drawBitmap(pixelated, expandedRect.left.toFloat(), expandedRect.top.toFloat(), null)
        
        faceBitmap.recycle()
        small.recycle()
        pixelated.recycle()
    }

    fun close() {
        genderClassifier.close()
        faceDetector.close()
    }
}
