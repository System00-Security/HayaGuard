package com.hayaguard.app

import android.app.Application
import android.os.Process
import android.util.Log

class HayaGuardApplication : Application() {

    companion object {
        private const val TAG = "HayaGuardApp"
        
        @Volatile
        private var instance: HayaGuardApplication? = null
        
        fun getInstance(): HayaGuardApplication? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Thread.currentThread().priority = Thread.MAX_PRIORITY
        
        warmupCronet()
        warmupDns()
        warmupBitmapPool()
        warmupGpu()
        
        Log.d(TAG, "Application initialized with warm network stack")
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "System low memory callback")
        MemoryManager.onLowMemory()
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d(TAG, "Trim memory level: $level")
        MemoryManager.onTrimMemory(level)
        
        when (level) {
            15, 80 -> {
                BitmapPool.clear()
                MemoryManager.requestGarbageCollection()
            }
            10, 5 -> {
                BitmapPool.trimToSize(MemoryManager.getMaxBitmapPoolSize() / 2)
            }
        }
    }

    private fun warmupCronet() {
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            try {
                CronetHelper.initialize(this@HayaGuardApplication)
                Log.d(TAG, "Cronet warmed up in background")
            } catch (e: Exception) {
                Log.e(TAG, "Cronet warmup failed: ${e.message}")
            }
        }.start()
    }

    private fun warmupDns() {
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST)
            try {
                DnsWarmer.warmUp()
                Log.d(TAG, "DNS warmed up in background")
            } catch (e: Exception) {
                Log.e(TAG, "DNS warmup failed: ${e.message}")
            }
        }.start()
    }
    
    private fun warmupBitmapPool() {
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            try {
                BitmapPool.preAllocate()
                Log.d(TAG, "BitmapPool pre-allocated in background")
            } catch (e: Exception) {
                Log.e(TAG, "BitmapPool warmup failed: ${e.message}")
            }
        }.start()
    }
    
    private fun warmupGpu() {
        TFLiteInterpreterFactory.eagerInitialize(this, "nsfwjs_quant.tflite")
    }
}
