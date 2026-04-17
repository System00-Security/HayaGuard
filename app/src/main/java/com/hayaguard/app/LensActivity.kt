package com.hayaguard.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class LensActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_URL = "extra_image_url"
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        private const val TAG = "LensActivity"
        
        private fun getDeviceUserAgent(): String {
            val model = android.os.Build.MODEL
            val buildId = android.os.Build.ID
            val androidVersion = android.os.Build.VERSION.RELEASE
            return "Mozilla/5.0 (Linux; Android $androidVersion; $model Build/$buildId; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36"
        }
    }

    private lateinit var ivLensImage: ImageView
    private lateinit var ocrOverlay: OcrOverlayView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHint: TextView
    private lateinit var btnClose: ImageButton

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var bitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lens)

        ivLensImage = findViewById(R.id.ivLensImage)
        ocrOverlay = findViewById(R.id.ocrOverlay)
        progressBar = findViewById(R.id.progressBar)
        tvHint = findViewById(R.id.tvHint)
        btnClose = findViewById(R.id.btnClose)

        ocrOverlay.setImageView(ivLensImage)
        btnClose.setOnClickListener { finish() }

        val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL)
        val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)

        when {
            !imagePath.isNullOrEmpty() -> loadFromFile(imagePath)
            !imageUrl.isNullOrEmpty() -> loadFromUrl(imageUrl)
            else -> {
                Toast.makeText(this, "No image provided", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun loadFromFile(path: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(path)
                if (file.exists()) {
                    bitmap = BitmapFactory.decodeFile(path)
                    bitmap?.let { bmp ->
                        withContext(Dispatchers.Main) {
                            displayImageAndRunOcr(bmp)
                        }
                    } ?: run {
                        withContext(Dispatchers.Main) {
                            showError("Failed to decode image")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showError("Image file not found")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading file: ${e.message}")
                withContext(Dispatchers.Main) {
                    showError("Failed to load image")
                }
            }
        }
    }

    private fun loadFromUrl(url: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", getDeviceUserAgent())
                connection.connect()

                val inputStream = connection.inputStream
                bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                connection.disconnect()

                bitmap?.let { bmp ->
                    withContext(Dispatchers.Main) {
                        displayImageAndRunOcr(bmp)
                    }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        showError("Failed to decode image")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading URL: ${e.message}")
                withContext(Dispatchers.Main) {
                    showError("Failed to load image: ${e.message}")
                }
            }
        }
    }

    private fun displayImageAndRunOcr(bmp: Bitmap) {
        ivLensImage.setImageBitmap(bmp)
        runTextRecognition(bmp)
    }

    private fun runTextRecognition(bmp: Bitmap) {
        val inputImage = InputImage.fromBitmap(bmp, 0)
        val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val devanagariRecognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())

        scope.launch {
            try {
                val allTextBlocks = mutableListOf<Text.TextBlock>()
                
                val latinResult = try {
                    latinRecognizer.process(inputImage).await()
                } catch (e: Exception) {
                    Log.e(TAG, "Latin OCR failed: ${e.message}")
                    null
                }
                latinResult?.textBlocks?.let { allTextBlocks.addAll(it) }

                val devanagariResult = try {
                    devanagariRecognizer.process(inputImage).await()
                } catch (e: Exception) {
                    Log.e(TAG, "Devanagari OCR failed: ${e.message}")
                    null
                }
                devanagariResult?.textBlocks?.let { blocks ->
                    for (block in blocks) {
                        val isDuplicate = allTextBlocks.any { existing ->
                            existing.boundingBox?.let { existingBox ->
                                block.boundingBox?.let { newBox ->
                                    val overlapX = maxOf(0, minOf(existingBox.right, newBox.right) - maxOf(existingBox.left, newBox.left))
                                    val overlapY = maxOf(0, minOf(existingBox.bottom, newBox.bottom) - maxOf(existingBox.top, newBox.top))
                                    val overlapArea = overlapX * overlapY
                                    val existingArea = existingBox.width() * existingBox.height()
                                    overlapArea > existingArea * 0.5
                                } ?: false
                            } ?: false
                        }
                        if (!isDuplicate) {
                            allTextBlocks.add(block)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (allTextBlocks.isNotEmpty()) {
                        ocrOverlay.setTextBlocks(allTextBlocks, bmp.width, bmp.height)
                        tvHint.visibility = View.VISIBLE
                    } else {
                        tvHint.text = "No text found"
                        tvHint.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "OCR failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvHint.text = "OCR failed"
                    tvHint.visibility = View.VISIBLE
                }
            } finally {
                latinRecognizer.close()
                devanagariRecognizer.close()
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        progressBar.visibility = View.GONE
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        bitmap?.recycle()
        bitmap = null
    }
}
