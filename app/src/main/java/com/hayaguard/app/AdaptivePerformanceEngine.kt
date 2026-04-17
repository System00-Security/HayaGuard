package com.hayaguard.app

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import org.tensorflow.lite.gpu.CompatibilityList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class DeviceTier {
    LOW_END,
    MID_RANGE,
    HIGH_END
}

object DeviceCapabilityProfiler {

    private const val TAG = "DeviceProfiler"
    private var cachedTier: DeviceTier? = null
    private var cachedScore: Int = -1
    private var gpuAvailable: Boolean? = null
    private var totalRamMB: Long = 0
    private var availableRamMB: Long = 0
    private var activityManager: ActivityManager? = null

    fun profile(context: Context): DeviceTier {
        cachedTier?.let { return it }
        
        activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val ramScore = calculateRamScore(context)
        val cpuScore = calculateCpuScore()
        val gpuScore = calculateGpuScore()

        val totalScore = ramScore + cpuScore + gpuScore
        cachedScore = totalScore

        val tier = when {
            totalScore >= 8 -> DeviceTier.HIGH_END
            totalScore >= 5 -> DeviceTier.MID_RANGE
            else -> DeviceTier.LOW_END
        }

        cachedTier = tier
        Log.d(TAG, "Device profiled: tier=$tier, score=$totalScore (RAM=$ramScore, CPU=$cpuScore, GPU=$gpuScore)")
        return tier
    }

    fun getTier(): DeviceTier {
        return cachedTier ?: DeviceTier.MID_RANGE
    }

    fun getPerformanceScore(): Int {
        return if (cachedScore >= 0) cachedScore else 5
    }

    fun isGpuAvailable(): Boolean {
        return gpuAvailable ?: false
    }
    
    fun getTotalRamMB(): Long {
        return totalRamMB
    }
    
    fun getAvailableRamMB(): Long {
        activityManager?.let { am ->
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            availableRamMB = memInfo.availMem / (1024 * 1024)
        }
        return availableRamMB
    }
    
    fun isLowMemory(): Boolean {
        activityManager?.let { am ->
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            return memInfo.lowMemory
        }
        return false
    }
    
    fun getMemoryPressureLevel(): MemoryPressure {
        val available = getAvailableRamMB()
        val total = totalRamMB
        if (total == 0L) return MemoryPressure.NORMAL
        
        val usedPercent = ((total - available) * 100 / total).toInt()
        return when {
            usedPercent >= 90 -> MemoryPressure.CRITICAL
            usedPercent >= 80 -> MemoryPressure.HIGH
            usedPercent >= 70 -> MemoryPressure.MODERATE
            else -> MemoryPressure.NORMAL
        }
    }

    private fun calculateRamScore(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        totalRamMB = (memInfo.totalMem / (1024 * 1024))
        availableRamMB = (memInfo.availMem / (1024 * 1024))

        return when {
            totalRamGB >= 8.0 -> 4
            totalRamGB >= 6.0 -> 3
            totalRamGB >= 4.0 -> 2
            totalRamGB >= 3.0 -> 1
            else -> 0
        }
    }

    private fun calculateCpuScore(): Int {
        val cores = Runtime.getRuntime().availableProcessors()

        return when {
            cores >= 8 -> 4
            cores >= 6 -> 3
            cores >= 4 -> 2
            else -> 1
        }
    }

    private fun calculateGpuScore(): Int {
        return try {
            val compatList = CompatibilityList()
            val isSupported = compatList.isDelegateSupportedOnThisDevice
            gpuAvailable = isSupported
            if (isSupported) 2 else 0
        } catch (e: Exception) {
            gpuAvailable = false
            0
        }
    }

    fun reset() {
        cachedTier = null
        cachedScore = -1
        gpuAvailable = null
    }
    
    enum class MemoryPressure {
        NORMAL,
        MODERATE,
        HIGH,
        CRITICAL
    }
}

object MemoryManager {
    
    private const val TAG = "MemoryManager"
    private val lastGcTime = AtomicLong(0)
    private const val MIN_GC_INTERVAL_MS = 5000L
    
    fun getMaxBitmapPoolSize(): Int {
        return when (DeviceCapabilityProfiler.getTier()) {
            DeviceTier.LOW_END -> 2 * 1024 * 1024
            DeviceTier.MID_RANGE -> 6 * 1024 * 1024
            DeviceTier.HIGH_END -> 12 * 1024 * 1024
        }
    }
    
    fun getMaxConcurrentImages(): Int {
        val pressure = DeviceCapabilityProfiler.getMemoryPressureLevel()
        val baseConcurrent = when (DeviceCapabilityProfiler.getTier()) {
            DeviceTier.LOW_END -> 1
            DeviceTier.MID_RANGE -> 2
            DeviceTier.HIGH_END -> 4
        }
        
        return when (pressure) {
            DeviceCapabilityProfiler.MemoryPressure.CRITICAL -> 1
            DeviceCapabilityProfiler.MemoryPressure.HIGH -> maxOf(1, baseConcurrent / 2)
            DeviceCapabilityProfiler.MemoryPressure.MODERATE -> maxOf(1, baseConcurrent - 1)
            DeviceCapabilityProfiler.MemoryPressure.NORMAL -> baseConcurrent
        }
    }
    
    fun shouldSkipProcessing(): Boolean {
        return DeviceCapabilityProfiler.getMemoryPressureLevel() == DeviceCapabilityProfiler.MemoryPressure.CRITICAL
    }
    
    fun shouldUseReducedQuality(): Boolean {
        val pressure = DeviceCapabilityProfiler.getMemoryPressureLevel()
        return pressure == DeviceCapabilityProfiler.MemoryPressure.HIGH || 
               pressure == DeviceCapabilityProfiler.MemoryPressure.CRITICAL
    }
    
    fun requestGarbageCollection() {
        val now = System.currentTimeMillis()
        val lastGc = lastGcTime.get()
        if (now - lastGc > MIN_GC_INTERVAL_MS) {
            if (lastGcTime.compareAndSet(lastGc, now)) {
                System.gc()
                Log.d(TAG, "GC requested, available RAM: ${DeviceCapabilityProfiler.getAvailableRamMB()}MB")
            }
        }
    }
    
    fun onLowMemory() {
        Log.w(TAG, "Low memory warning received")
        BitmapPool.clear()
        requestGarbageCollection()
    }
    
    fun onTrimMemory(level: Int) {
        when {
            level >= 80 -> {
                Log.w(TAG, "Critical memory trim level: $level")
                BitmapPool.clear()
                requestGarbageCollection()
            }
            level >= 60 -> {
                Log.w(TAG, "High memory trim level: $level")
                BitmapPool.trimToSize(getMaxBitmapPoolSize() / 2)
            }
            level >= 40 -> {
                Log.d(TAG, "Moderate memory trim level: $level")
                BitmapPool.trimToSize(getMaxBitmapPoolSize())
            }
        }
    }
}

object AdaptivePerformanceEngine {

    private const val TAG = "AdaptiveEngine"

    private var processingDispatcher: CoroutineDispatcher? = null
    private var executorService: java.util.concurrent.ExecutorService? = null

    private class LowPriorityThreadFactory(private val namePrefix: String) : ThreadFactory {
        private val threadNumber = AtomicInteger(1)
        
        override fun newThread(r: Runnable): Thread {
            return Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND + Process.THREAD_PRIORITY_MORE_FAVORABLE)
                try {
                    r.run()
                } catch (e: Throwable) {
                    Log.e(TAG, "Thread crashed: ${e.message}", e)
                    if (e.message?.contains("gpu", ignoreCase = true) == true ||
                        e.message?.contains("delegate", ignoreCase = true) == true ||
                        e.message?.contains("tensorflow", ignoreCase = true) == true) {
                        TFLiteInterpreterFactory.markGpuCrashed()
                    }
                }
            }, "$namePrefix-${threadNumber.getAndIncrement()}").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
                setUncaughtExceptionHandler { _, throwable ->
                    Log.e(TAG, "Uncaught exception in AI thread: ${throwable.message}", throwable)
                    TFLiteInterpreterFactory.markGpuCrashed()
                }
            }
        }
    }

    fun initialize(context: Context) {
        val tier = DeviceCapabilityProfiler.profile(context)
        setupDispatcher(tier)
        Log.d(TAG, "Engine initialized for $tier: inputSize=${getInputSize()}, threads=${getThreadCount()}, timeout=${getTimeoutMs()}ms, RAM=${DeviceCapabilityProfiler.getTotalRamMB()}MB")
    }

    private fun setupDispatcher(tier: DeviceTier) {
        executorService?.shutdown()

        val threadCount = getThreadCount(tier)
        executorService = Executors.newFixedThreadPool(threadCount, LowPriorityThreadFactory("HayaGuard-AI"))
        processingDispatcher = executorService!!.asCoroutineDispatcher()
    }

    fun getDispatcher(): CoroutineDispatcher {
        return processingDispatcher ?: kotlinx.coroutines.Dispatchers.Default
    }

    fun getInputSize(): Int {
        if (MemoryManager.shouldUseReducedQuality()) {
            return 128
        }
        return getInputSize(DeviceCapabilityProfiler.getTier())
    }

    fun getInputSize(tier: DeviceTier): Int {
        return when (tier) {
            DeviceTier.LOW_END -> 128
            DeviceTier.MID_RANGE -> 224
            DeviceTier.HIGH_END -> 300
        }
    }

    fun getNsfwjsInputSize(): Int {
        if (MemoryManager.shouldUseReducedQuality()) {
            return 128
        }
        return when (DeviceCapabilityProfiler.getTier()) {
            DeviceTier.LOW_END -> 128
            DeviceTier.MID_RANGE -> 224
            DeviceTier.HIGH_END -> 224
        }
    }

    fun getNudeNetInputSize(): Int {
        if (MemoryManager.shouldUseReducedQuality()) {
            return 160
        }
        return when (DeviceCapabilityProfiler.getTier()) {
            DeviceTier.LOW_END -> 160
            DeviceTier.MID_RANGE -> 320
            DeviceTier.HIGH_END -> 320
        }
    }

    fun getThreadCount(): Int {
        return getThreadCount(DeviceCapabilityProfiler.getTier())
    }

    fun getThreadCount(tier: DeviceTier): Int {
        return when (tier) {
            DeviceTier.LOW_END -> 1
            DeviceTier.MID_RANGE -> 2
            DeviceTier.HIGH_END -> 4
        }
    }

    fun getTimeoutMs(): Long {
        val pressure = DeviceCapabilityProfiler.getMemoryPressureLevel()
        val baseTimeout = getTimeoutMs(DeviceCapabilityProfiler.getTier())
        return when (pressure) {
            DeviceCapabilityProfiler.MemoryPressure.CRITICAL -> baseTimeout + 1000L
            DeviceCapabilityProfiler.MemoryPressure.HIGH -> baseTimeout + 500L
            else -> baseTimeout
        }
    }

    fun getTimeoutMs(tier: DeviceTier): Long {
        return when (tier) {
            DeviceTier.LOW_END -> 2000L
            DeviceTier.MID_RANGE -> 1500L
            DeviceTier.HIGH_END -> 1000L
        }
    }

    fun getTimeoutFallbackAction(): TimeoutFallbackAction {
        return when (DeviceCapabilityProfiler.getTier()) {
            DeviceTier.LOW_END -> TimeoutFallbackAction.SHOW
            DeviceTier.MID_RANGE -> TimeoutFallbackAction.BLUR
            DeviceTier.HIGH_END -> TimeoutFallbackAction.BLUR
        }
    }

    fun getCpuThreadsForInterpreter(): Int {
        val pressure = DeviceCapabilityProfiler.getMemoryPressureLevel()
        val baseThreads = when (DeviceCapabilityProfiler.getTier()) {
            DeviceTier.LOW_END -> 2
            DeviceTier.MID_RANGE -> 3
            DeviceTier.HIGH_END -> 4
        }
        return when (pressure) {
            DeviceCapabilityProfiler.MemoryPressure.CRITICAL -> 1
            DeviceCapabilityProfiler.MemoryPressure.HIGH -> maxOf(1, baseThreads - 1)
            else -> baseThreads
        }
    }

    fun shouldUseGpu(): Boolean {
        if (DeviceCapabilityProfiler.getMemoryPressureLevel() == DeviceCapabilityProfiler.MemoryPressure.CRITICAL) {
            return false
        }
        return DeviceCapabilityProfiler.isGpuAvailable()
    }
    
    fun shouldSkipNudeNet(): Boolean {
        val tier = DeviceCapabilityProfiler.getTier()
        val pressure = DeviceCapabilityProfiler.getMemoryPressureLevel()
        return tier == DeviceTier.LOW_END || pressure == DeviceCapabilityProfiler.MemoryPressure.CRITICAL
    }
    
    fun getPreloadDistance(): Int {
        if (SettingsManager.isBatterySaverEnabled()) {
            return 0
        }
        val pressure = DeviceCapabilityProfiler.getMemoryPressureLevel()
        if (pressure == DeviceCapabilityProfiler.MemoryPressure.CRITICAL) {
            return 0
        }
        return when (DeviceCapabilityProfiler.getTier()) {
            DeviceTier.LOW_END -> 500
            DeviceTier.MID_RANGE -> 1000
            DeviceTier.HIGH_END -> 1500
        }
    }
    
    fun getMaxPreloadItems(): Int {
        if (SettingsManager.isBatterySaverEnabled()) {
            return 0
        }
        val pressure = DeviceCapabilityProfiler.getMemoryPressureLevel()
        if (pressure != DeviceCapabilityProfiler.MemoryPressure.NORMAL) {
            return 0
        }
        return when (DeviceCapabilityProfiler.getTier()) {
            DeviceTier.LOW_END -> 0
            DeviceTier.MID_RANGE -> 3
            DeviceTier.HIGH_END -> 6
        }
    }

    fun shutdown() {
        executorService?.shutdown()
        executorService = null
        processingDispatcher = null
    }

    enum class TimeoutFallbackAction {
        BLUR,
        SHOW
    }
}
