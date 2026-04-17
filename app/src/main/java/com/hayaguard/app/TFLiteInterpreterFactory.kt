package com.hayaguard.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

object TFLiteInterpreterFactory {

    private const val TAG = "TFLiteFactory"
    private const val PREFS_NAME = "gpu_stability_prefs"
    private const val KEY_GPU_STABLE = "gpu_stable"
    private const val KEY_GPU_TEST_COUNT = "gpu_test_count"
    private const val KEY_GPU_CRASH_COUNT = "gpu_crash_count"
    private const val KEY_LAST_TEST_TIME = "last_test_time"
    private const val GPU_TEST_RUNS = 3
    private const val GPU_STABILITY_THRESHOLD = 0.7f
    private const val RETEST_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L

    private val GPU_BLACKLISTED_MANUFACTURERS = setOf(
        "vivo", "oppo", "itel", "tecno", "infinix"
    )

    private val GPU_BLACKLISTED_MODELS = setOf(
        "v2026", "v2027", "v2029", "v2061", "v2111", "v2120", "cph"
    )

    enum class DelegateType {
        GPU, NNAPI, CPU
    }

    data class InterpreterResult(
        val interpreter: Interpreter,
        val delegateType: DelegateType,
        val gpuDelegate: GpuDelegate?
    )

    @Volatile
    private var cachedResult: InterpreterResult? = null
    @Volatile
    private var gpuCrashed = false
    @Volatile
    private var gpuTestedStable: Boolean? = null
    private val lock = Any()
    private var modelBufferCache: MappedByteBuffer? = null
    private var contextRef: Context? = null
    private var modelNameCache: String? = null
    private var prefs: SharedPreferences? = null

    fun createInterpreter(context: Context, modelName: String): InterpreterResult {
        synchronized(lock) {
            cachedResult?.let { return it }

            contextRef = context.applicationContext
            modelNameCache = modelName
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            val modelBuffer = loadModelFile(context, modelName)
            modelBufferCache = modelBuffer
            val result = tryCreateWithFallback(context, modelBuffer)
            cachedResult = result
            Log.d(TAG, "Initialized with ${result.delegateType}")
            return result
        }
    }

    private fun isGpuBlacklisted(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        
        if (GPU_BLACKLISTED_MANUFACTURERS.any { manufacturer.contains(it) }) {
            Log.d(TAG, "GPU blacklisted for manufacturer: $manufacturer")
            return true
        }
        if (GPU_BLACKLISTED_MODELS.any { model.contains(it) }) {
            Log.d(TAG, "GPU blacklisted for model: $model")
            return true
        }
        return gpuCrashed
    }

    private fun shouldTestGpu(): Boolean {
        val savedStable = prefs?.getBoolean(KEY_GPU_STABLE, false) ?: false
        val lastTest = prefs?.getLong(KEY_LAST_TEST_TIME, 0L) ?: 0L
        val now = System.currentTimeMillis()
        
        if (savedStable && (now - lastTest) < RETEST_INTERVAL_MS) {
            gpuTestedStable = true
            return false
        }
        return true
    }

    private fun testGpuStability(context: Context, modelBuffer: MappedByteBuffer): Boolean {
        if (!shouldTestGpu()) {
            return gpuTestedStable == true
        }

        val compatList = CompatibilityList()
        if (!compatList.isDelegateSupportedOnThisDevice) {
            return false
        }

        var successCount = 0
        val testInput = ByteBuffer.allocateDirect(4 * 224 * 224 * 3).apply {
            order(ByteOrder.nativeOrder())
            for (i in 0 until 224 * 224 * 3) {
                putFloat(0.5f)
            }
            rewind()
        }

        for (i in 0 until GPU_TEST_RUNS) {
            var testInterpreter: Interpreter? = null
            var testDelegate: GpuDelegate? = null
            try {
                testDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
                val options = Interpreter.Options().apply {
                    addDelegate(testDelegate)
                }
                testInterpreter = Interpreter(modelBuffer.duplicate(), options)
                
                val output = Array(1) { FloatArray(5) }
                testInterpreter.run(testInput.duplicate(), output)
                
                val hasValidOutput = output[0].any { it.isFinite() }
                if (hasValidOutput) {
                    successCount++
                }
                
                testInterpreter.close()
                testDelegate.close()
            } catch (e: Exception) {
                Log.w(TAG, "GPU test run $i failed: ${e.message}")
                try { testInterpreter?.close() } catch (_: Exception) {}
                try { testDelegate?.close() } catch (_: Exception) {}
            }
        }

        val stabilityRatio = successCount.toFloat() / GPU_TEST_RUNS
        val isStable = stabilityRatio >= GPU_STABILITY_THRESHOLD
        
        gpuTestedStable = isStable
        prefs?.edit()?.apply {
            putBoolean(KEY_GPU_STABLE, isStable)
            putLong(KEY_LAST_TEST_TIME, System.currentTimeMillis())
            putInt(KEY_GPU_TEST_COUNT, (prefs?.getInt(KEY_GPU_TEST_COUNT, 0) ?: 0) + 1)
            apply()
        }
        
        Log.d(TAG, "GPU stability test: $successCount/$GPU_TEST_RUNS passed, stable=$isStable")
        return isStable
    }

    private fun tryCreateWithFallback(context: Context, modelBuffer: MappedByteBuffer): InterpreterResult {
        if (!isGpuBlacklisted()) {
            val compatList = CompatibilityList()
            
            if (compatList.isDelegateSupportedOnThisDevice) {
                val gpuStable = testGpuStability(context, modelBuffer)
                
                if (gpuStable) {
                    try {
                        val gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
                        val options = Interpreter.Options().apply {
                            addDelegate(gpuDelegate)
                        }
                        val interpreter = Interpreter(modelBuffer, options)
                        return InterpreterResult(interpreter, DelegateType.GPU, gpuDelegate)
                    } catch (e: Exception) {
                        Log.w(TAG, "GPU failed: ${e.message}")
                        recordGpuFailure()
                    }
                } else {
                    Log.d(TAG, "GPU skipped due to stability test failure")
                }
            }
        }

        try {
            val options = Interpreter.Options().apply {
                setUseNNAPI(true)
            }
            val interpreter = Interpreter(modelBuffer, options)
            return InterpreterResult(interpreter, DelegateType.NNAPI, null)
        } catch (e: Exception) {
            Log.w(TAG, "NNAPI failed: ${e.message}")
        }

        val cpuThreads = AdaptivePerformanceEngine.getCpuThreadsForInterpreter()
        val options = Interpreter.Options().apply {
            setNumThreads(cpuThreads)
        }
        val interpreter = Interpreter(modelBuffer, options)
        return InterpreterResult(interpreter, DelegateType.CPU, null)
    }

    private fun recordGpuFailure() {
        val crashCount = (prefs?.getInt(KEY_GPU_CRASH_COUNT, 0) ?: 0) + 1
        prefs?.edit()?.apply {
            putInt(KEY_GPU_CRASH_COUNT, crashCount)
            if (crashCount >= 2) {
                putBoolean(KEY_GPU_STABLE, false)
            }
            apply()
        }
    }

    fun markGpuCrashed() {
        synchronized(lock) {
            if (!gpuCrashed) {
                gpuCrashed = true
                gpuTestedStable = false
                recordGpuFailure()
                Log.w(TAG, "GPU marked as crashed, will use CPU fallback")
                recreateWithCpu()
            }
        }
    }

    private fun recreateWithCpu() {
        try {
            cachedResult?.let {
                it.gpuDelegate?.close()
                it.interpreter.close()
            }
            cachedResult = null

            val ctx = contextRef ?: return
            val name = modelNameCache ?: return
            val modelBuffer = loadModelFile(ctx, name)
            
            val cpuThreads = AdaptivePerformanceEngine.getCpuThreadsForInterpreter()
            val options = Interpreter.Options().apply {
                setNumThreads(cpuThreads)
            }
            val interpreter = Interpreter(modelBuffer, options)
            cachedResult = InterpreterResult(interpreter, DelegateType.CPU, null)
            Log.d(TAG, "Recreated with CPU fallback")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recreate with CPU: ${e.message}")
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelName)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun close() {
        synchronized(lock) {
            cachedResult?.let {
                it.gpuDelegate?.close()
                it.interpreter.close()
            }
            cachedResult = null
            modelBufferCache = null
            contextRef = null
        }
    }
    
    fun eagerInitialize(context: Context, modelName: String) {
        Thread {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                createInterpreter(context.applicationContext, modelName)
                Log.d(TAG, "Eager GPU initialization completed")
            } catch (e: Exception) {
                Log.e(TAG, "Eager GPU initialization failed: ${e.message}")
            }
        }.start()
    }
}
