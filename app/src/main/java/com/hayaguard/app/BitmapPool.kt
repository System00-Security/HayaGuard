package com.hayaguard.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

object BitmapPool {

    private val pools = ConcurrentHashMap<Int, ConcurrentLinkedQueue<Bitmap>>()
    private val currentSize = AtomicLong(0)
    
    private fun getMaxPoolSize(): Int {
        return MemoryManager.getMaxBitmapPoolSize()
    }
    
    private fun getPoolLimit(): Int {
        return when (DeviceCapabilityProfiler.getTier()) {
            DeviceTier.LOW_END -> 4
            DeviceTier.MID_RANGE -> 6
            DeviceTier.HIGH_END -> 8
        }
    }

    private fun getPool(size: Int): ConcurrentLinkedQueue<Bitmap> {
        return pools.getOrPut(size) { ConcurrentLinkedQueue() }
    }

    fun acquire(size: Int): Bitmap {
        val pooled = getPool(size).poll()
        if (pooled != null) {
            val bitmapSize = pooled.byteCount.toLong()
            currentSize.addAndGet(-bitmapSize)
            return pooled
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    }

    fun acquire224(): Bitmap = acquire(224)

    fun acquire320(): Bitmap = acquire(320)

    fun release(bitmap: Bitmap) {
        if (bitmap.isRecycled) return

        val size = bitmap.width
        if (bitmap.width == bitmap.height) {
            val pool = getPool(size)
            val bitmapSize = bitmap.byteCount.toLong()
            
            if (pool.size < getPoolLimit() && currentSize.get() + bitmapSize < getMaxPoolSize()) {
                bitmap.eraseColor(0)
                pool.offer(bitmap)
                currentSize.addAndGet(bitmapSize)
                return
            }
        }
        bitmap.recycle()
    }

    fun scaleToSize(source: Bitmap, targetSize: Int): Bitmap {
        val target = acquire(targetSize)
        val canvas = Canvas(target)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val scaleX = targetSize.toFloat() / source.width
        val scaleY = targetSize.toFloat() / source.height
        canvas.scale(scaleX, scaleY)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return target
    }

    fun scaleTo224(source: Bitmap): Bitmap = scaleToSize(source, 224)

    fun scaleTo320(source: Bitmap): Bitmap = scaleToSize(source, 320)

    fun scaleAdaptive(source: Bitmap): Bitmap {
        val size = AdaptivePerformanceEngine.getInputSize()
        return scaleToSize(source, size)
    }

    fun scaleForNsfwjs(source: Bitmap): Bitmap {
        val size = AdaptivePerformanceEngine.getNsfwjsInputSize()
        return scaleToSize(source, size)
    }

    fun scaleForNudeNet(source: Bitmap): Bitmap {
        val size = AdaptivePerformanceEngine.getNudeNetInputSize()
        return scaleToSize(source, size)
    }
    
    fun trimToSize(maxSize: Int) {
        while (currentSize.get() > maxSize) {
            var removed = false
            for (pool in pools.values) {
                val bitmap = pool.poll()
                if (bitmap != null && !bitmap.isRecycled) {
                    val bitmapSize = bitmap.byteCount.toLong()
                    currentSize.addAndGet(-bitmapSize)
                    bitmap.recycle()
                    removed = true
                    break
                }
            }
            if (!removed) break
        }
    }

    fun clear() {
        pools.values.forEach { pool ->
            pool.forEach { if (!it.isRecycled) it.recycle() }
            pool.clear()
        }
        pools.clear()
        currentSize.set(0)
    }
    
    fun getCurrentSize(): Long = currentSize.get()
    
    fun preAllocate() {
        val sizesToPreAllocate = listOf(224, 320)
        val countPerSize = when (DeviceCapabilityProfiler.getTier()) {
            DeviceTier.LOW_END -> 1
            DeviceTier.MID_RANGE -> 2
            DeviceTier.HIGH_END -> 3
        }
        
        for (size in sizesToPreAllocate) {
            val pool = getPool(size)
            repeat(countPerSize) {
                if (currentSize.get() < getMaxPoolSize()) {
                    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    pool.offer(bitmap)
                    currentSize.addAndGet(bitmap.byteCount.toLong())
                }
            }
        }
    }
}
