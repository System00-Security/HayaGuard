package com.hayaguard.app

import android.content.Context
import android.util.Log
import com.google.android.gms.net.CronetProviderInstaller
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import android.os.Build

object CronetHelper {

    private fun getDeviceUserAgent(): String {
        val model = Build.MODEL
        val buildId = Build.ID
        val androidVersion = Build.VERSION.RELEASE
        return "Mozilla/5.0 (Linux; Android $androidVersion; $model Build/$buildId; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    private const val TAG = "CronetHelper"

    @Volatile
    private var cronetEngine: CronetEngine? = null
    private val executor: Executor = Executors.newFixedThreadPool(6)
    private val isInitialized = AtomicBoolean(false)
    private val isInitializing = AtomicBoolean(false)
    private val initLatch = CountDownLatch(1)
    private var quicEnabled = false

    fun initialize(context: Context) {
        if (isInitialized.get()) return
        if (!isInitializing.compareAndSet(false, true)) return
        
        CronetProviderInstaller.installProvider(context).addOnCompleteListener { task ->
            try {
                if (task.isSuccessful) {
                    createEngine(context)
                    quicEnabled = true
                    Log.d(TAG, "Cronet initialized with QUIC support")
                } else {
                    createFallbackEngine(context)
                    Log.d(TAG, "Cronet initialized without QUIC (fallback)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cronet init failed: ${e.message}")
                createFallbackEngine(context)
            }
            isInitialized.set(true)
            initLatch.countDown()
        }
    }

    private fun createEngine(context: Context) {
        val cacheDir = File(context.cacheDir, "cronet_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val builder = CronetEngine.Builder(context)
            .enableQuic(true)
            .enableHttp2(true)
            .enableBrotli(true)
            .setStoragePath(cacheDir.absolutePath)
            .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, 50 * 1024 * 1024)
            .setUserAgent(getDeviceUserAgent())

        addQuicHints(builder)
        
        cronetEngine = builder.build()
    }

    private fun addQuicHints(builder: CronetEngine.Builder) {
        val quicDomains = listOf(
            "scontent.fdac24-1.fna.fbcdn.net",
            "scontent.fdac24-2.fna.fbcdn.net",
            "scontent.fdac24-3.fna.fbcdn.net",
            "scontent.fdac24-4.fna.fbcdn.net",
            "scontent.xx.fbcdn.net",
            "scontent-sin.xx.fbcdn.net",
            "scontent-sin1-1.xx.fbcdn.net",
            "scontent-sin2-1.xx.fbcdn.net",
            "static.xx.fbcdn.net",
            "video.xx.fbcdn.net",
            "external.xx.fbcdn.net",
            "z-m-scontent.fdac24-1.fna.fbcdn.net",
            "z-m-scontent.fdac24-2.fna.fbcdn.net",
            "edge-chat.facebook.com",
            "mqtt.facebook.com"
        )
        
        for (domain in quicDomains) {
            builder.addQuicHint(domain, 443, 443)
        }
    }

    private fun createFallbackEngine(context: Context) {
        val cacheDir = File(context.cacheDir, "cronet_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        cronetEngine = CronetEngine.Builder(context)
            .enableHttp2(true)
            .enableBrotli(true)
            .setStoragePath(cacheDir.absolutePath)
            .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, 50 * 1024 * 1024)
            .setUserAgent(getDeviceUserAgent())
            .build()
    }

    fun getEngine(): CronetEngine? {
        if (!isInitialized.get()) {
            initLatch.await(5, TimeUnit.SECONDS)
        }
        return cronetEngine
    }

    fun getExecutor(): Executor = executor
    
    fun isQuicEnabled(): Boolean = quicEnabled

    fun isFacebookCdn(url: String): Boolean {
        return url.contains("fbcdn.net") || url.contains("facebook.com")
    }

    fun fetchImage(
        url: String,
        headers: Map<String, String>
    ): CronetResponse? {
        val engine = getEngine() ?: return null

        val responseLatch = CountDownLatch(1)
        val dataOutputStream = ByteArrayOutputStream(64 * 1024)
        val responseInfoRef = AtomicReference<UrlResponseInfo?>(null)
        val failed = AtomicBoolean(false)
        val errorRef = AtomicReference<String?>(null)

        val callback = object : UrlRequest.Callback() {
            override fun onRedirectReceived(
                request: UrlRequest,
                info: UrlResponseInfo,
                newLocationUrl: String
            ) {
                request.followRedirect()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                responseInfoRef.set(info)
                
                val protocol = info.negotiatedProtocol
                Log.d(TAG, "Protocol: $protocol for ${url.take(80)}")
                
                if (info.httpStatusCode == 200) {
                    request.read(ByteBuffer.allocateDirect(64 * 1024))
                } else {
                    failed.set(true)
                    errorRef.set("HTTP ${info.httpStatusCode}")
                    responseLatch.countDown()
                }
            }

            override fun onReadCompleted(
                request: UrlRequest,
                info: UrlResponseInfo,
                byteBuffer: ByteBuffer
            ) {
                byteBuffer.flip()
                if (byteBuffer.hasRemaining()) {
                    val bytes = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(bytes)
                    synchronized(dataOutputStream) {
                        dataOutputStream.write(bytes)
                    }
                }
                byteBuffer.clear()
                request.read(byteBuffer)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                responseLatch.countDown()
            }

            override fun onFailed(
                request: UrlRequest,
                info: UrlResponseInfo?,
                error: CronetException
            ) {
                failed.set(true)
                errorRef.set(error.message)
                Log.e(TAG, "Request failed: ${error.message}")
                responseLatch.countDown()
            }

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                failed.set(true)
                errorRef.set("Canceled")
                responseLatch.countDown()
            }
        }

        val requestBuilder = engine.newUrlRequestBuilder(url, callback, executor)
            .setHttpMethod("GET")
            .setPriority(UrlRequest.Builder.REQUEST_PRIORITY_HIGHEST)

        headers.forEach { (key, value) ->
            val lowerKey = key.lowercase()
            if (lowerKey != "host" && lowerKey != "content-length") {
                requestBuilder.addHeader(key, value)
            }
        }

        requestBuilder.addHeader("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
        requestBuilder.addHeader("Accept-Encoding", "gzip, deflate, br")

        val request = requestBuilder.build()
        request.start()

        if (!responseLatch.await(15, TimeUnit.SECONDS)) {
            request.cancel()
            Log.e(TAG, "Request timeout for ${url.take(80)}")
            return null
        }

        if (failed.get()) {
            Log.e(TAG, "Request failed: ${errorRef.get()} for ${url.take(80)}")
            return null
        }

        val info = responseInfoRef.get() ?: return null
        if (info.httpStatusCode != 200) {
            return null
        }

        val data = synchronized(dataOutputStream) {
            dataOutputStream.toByteArray()
        }
        
        if (data.isEmpty()) {
            return null
        }

        val contentType = info.allHeaders["content-type"]?.firstOrNull()
            ?: info.allHeaders["Content-Type"]?.firstOrNull()
            ?: "image/jpeg"

        return CronetResponse(
            data = data,
            mimeType = contentType.split(";").first().trim(),
            statusCode = info.httpStatusCode,
            protocol = info.negotiatedProtocol
        )
    }

    data class CronetResponse(
        val data: ByteArray,
        val mimeType: String,
        val statusCode: Int,
        val protocol: String
    )

    fun shutdown() {
        cronetEngine?.shutdown()
        cronetEngine = null
        isInitialized.set(false)
    }
}
