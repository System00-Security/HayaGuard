package com.hayaguard.app

import android.graphics.Bitmap
import android.graphics.Color
import android.util.LruCache
import kotlin.math.abs

object ImagePreFilter {

    private const val MIN_WIDTH = 80
    private const val MIN_HEIGHT = 80
    private const val SAMPLE_STEP = 5
    private const val CACHE_SIZE = 100
    
    private val colorAnalysisCache = LruCache<Long, ColorAnalysis>(CACHE_SIZE)

    data class FilterResult(
        val shouldScan: Boolean,
        val reason: String
    )
    
    private fun computeBitmapHash(bitmap: Bitmap): Long {
        val w = bitmap.width
        val h = bitmap.height
        var hash = (w.toLong() shl 32) or h.toLong()
        val samplePoints = listOf(
            0 to 0, w/2 to 0, w-1 to 0,
            0 to h/2, w/2 to h/2, w-1 to h/2,
            0 to h-1, w/2 to h-1, w-1 to h-1
        )
        for ((x, y) in samplePoints) {
            val safeX = x.coerceIn(0, w - 1)
            val safeY = y.coerceIn(0, h - 1)
            hash = hash xor (bitmap.getPixel(safeX, safeY).toLong() * 31)
        }
        return hash
    }

    fun shouldScanImage(bitmap: Bitmap): FilterResult {
        if (bitmap.width < MIN_WIDTH || bitmap.height < MIN_HEIGHT) {
            return FilterResult(false, "too_small")
        }

        val colorAnalysis = getColorAnalysisCached(bitmap)
        
        if (colorAnalysis.isGrayscale) {
            return FilterResult(true, "grayscale_scan")
        }
        
        if (colorAnalysis.hasFleshTones) {
            return FilterResult(true, "flesh_detected")
        }
        
        if (colorAnalysis.hasAnimatedStyle) {
            return FilterResult(true, "animated_style")
        }
        
        if (colorAnalysis.hasHighSaturation && colorAnalysis.skinRatio > 0.05f) {
            return FilterResult(true, "colorful_with_skin")
        }

        if (colorAnalysis.textLikeRatio > 0.60f) {
            return FilterResult(false, "text_heavy")
        }

        if (colorAnalysis.dominantUI && colorAnalysis.skinRatio < 0.03f) {
            return FilterResult(false, "ui_element")
        }

        if (colorAnalysis.dominantNature && colorAnalysis.skinRatio < 0.05f) {
            return FilterResult(false, "nature_scene")
        }

        return FilterResult(true, "needs_scan")
    }

    private data class ColorAnalysis(
        val skinRatio: Float,
        val textLikeRatio: Float,
        val dominantNature: Boolean,
        val dominantUI: Boolean,
        val isGrayscale: Boolean,
        val hasFleshTones: Boolean,
        val hasHighSaturation: Boolean,
        val hasAnimatedStyle: Boolean
    )
    
    private fun getColorAnalysisCached(bitmap: Bitmap): ColorAnalysis {
        val hash = computeBitmapHash(bitmap)
        colorAnalysisCache.get(hash)?.let { return it }
        val analysis = analyzeColors(bitmap)
        colorAnalysisCache.put(hash, analysis)
        return analysis
    }
    
    fun clearCache() {
        colorAnalysisCache.evictAll()
    }

    private fun analyzeColors(bitmap: Bitmap): ColorAnalysis {
        var skinPixels = 0
        var textPixels = 0
        var naturePixels = 0
        var uiPixels = 0
        var grayPixels = 0
        var fleshPixels = 0
        var saturatedPixels = 0
        var animePixels = 0
        var totalSampled = 0

        val width = bitmap.width
        val height = bitmap.height

        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                totalSampled++

                if (isSkinTone(r, g, b)) {
                    skinPixels++
                }
                
                if (isFleshTone(r, g, b)) {
                    fleshPixels++
                }

                if (isTextLike(r, g, b)) {
                    textPixels++
                }

                if (isNature(r, g, b)) {
                    naturePixels++
                }

                if (isUIColor(r, g, b)) {
                    uiPixels++
                }
                
                if (isGrayscalePixel(r, g, b)) {
                    grayPixels++
                }
                
                if (isHighSaturation(r, g, b)) {
                    saturatedPixels++
                }
                
                if (isAnimeStyle(r, g, b)) {
                    animePixels++
                }

                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        val skinRatio = skinPixels.toFloat() / totalSampled
        val textRatio = textPixels.toFloat() / totalSampled
        val natureRatio = naturePixels.toFloat() / totalSampled
        val uiRatio = uiPixels.toFloat() / totalSampled
        val grayRatio = grayPixels.toFloat() / totalSampled
        val fleshRatio = fleshPixels.toFloat() / totalSampled
        val satRatio = saturatedPixels.toFloat() / totalSampled
        val animeRatio = animePixels.toFloat() / totalSampled

        return ColorAnalysis(
            skinRatio = skinRatio,
            textLikeRatio = textRatio,
            dominantNature = natureRatio > 0.50f,
            dominantUI = uiRatio > 0.70f,
            isGrayscale = grayRatio > 0.85f,
            hasFleshTones = fleshRatio > 0.08f,
            hasHighSaturation = satRatio > 0.20f,
            hasAnimatedStyle = animeRatio > 0.15f || (satRatio > 0.30f && fleshRatio > 0.03f)
        )
    }

    private fun isSkinTone(r: Int, g: Int, b: Int): Boolean {
        if (r < 40 || g < 20) return false
        if (r < g && r < b) return false
        
        val rgDiff = r - g
        val rbDiff = r - b
        
        val light = r > 180 && g > 140 && b > 100 && rgDiff in 5..60 && rbDiff in 10..100
        val medium = r in 120..220 && g in 80..180 && b in 50..160 && rgDiff in 10..80 && rbDiff in 20..120
        val dark = r in 60..150 && g in 40..120 && b in 20..100 && r > g && r > b
        
        return light || medium || dark
    }
    
    private fun isFleshTone(r: Int, g: Int, b: Int): Boolean {
        val h = rgbToHue(r, g, b)
        val s = rgbToSaturation(r, g, b)
        val v = maxOf(r, g, b) / 255f
        
        return h in 0f..50f && s in 0.15f..0.75f && v > 0.2f
    }
    
    private fun rgbToHue(r: Int, g: Int, b: Int): Float {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min
        if (delta < 0.001f) return 0f
        val h = when (max) {
            rf -> 60f * (((gf - bf) / delta) % 6)
            gf -> 60f * (((bf - rf) / delta) + 2)
            else -> 60f * (((rf - gf) / delta) + 4)
        }
        return if (h < 0) h + 360 else h
    }
    
    private fun rgbToSaturation(r: Int, g: Int, b: Int): Float {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        return if (max == 0) 0f else (max - min).toFloat() / max
    }
    
    private fun isGrayscalePixel(r: Int, g: Int, b: Int): Boolean {
        return abs(r - g) < 15 && abs(g - b) < 15 && abs(r - b) < 15
    }
    
    private fun isHighSaturation(r: Int, g: Int, b: Int): Boolean {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        return max > 50 && (max - min).toFloat() / max > 0.4f
    }

    private fun isTextLike(r: Int, g: Int, b: Int): Boolean {
        val isBlack = r < 30 && g < 30 && b < 30
        val isWhite = r > 235 && g > 235 && b > 235
        return isBlack || isWhite
    }

    private fun isNature(r: Int, g: Int, b: Int): Boolean {
        val isGreen = g > r + 30 && g > b + 30 && g > 100
        val isBlue = b > r + 40 && b > g + 20 && b > 140
        return isGreen || isBlue
    }

    private fun isUIColor(r: Int, g: Int, b: Int): Boolean {
        val isFacebookBlue = b > 200 && r < 80 && g < 130
        val isDarkMode = r < 25 && g < 25 && b < 30
        val isWhiteUI = r > 245 && g > 245 && b > 245
        return isFacebookBlue || isDarkMode || isWhiteUI
    }
    
    private fun isAnimeStyle(r: Int, g: Int, b: Int): Boolean {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val sat = if (max > 0) (max - min).toFloat() / max else 0f
        val isPastel = max > 180 && sat in 0.15f..0.50f
        val isVibrant = sat > 0.50f && max > 150
        val isSkinLikePink = r > 200 && g in 140..200 && b in 160..220
        val isAnimeSkin = r > 220 && g > 180 && b > 160 && r > g && g > b
        return isPastel || isVibrant || isSkinLikePink || isAnimeSkin
    }
}
