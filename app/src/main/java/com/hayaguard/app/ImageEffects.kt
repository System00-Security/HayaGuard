package com.hayaguard.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.io.ByteArrayOutputStream

object ImageEffects {

    private const val DEFAULT_PIXEL_SIZE = 32
    private const val PLACEHOLDER_BG_COLOR = "#F0F2F5"
    private const val PLACEHOLDER_CIRCLE_COLOR = "#E4E6EB"
    private const val PLACEHOLDER_SHIELD_COLOR = "#1877F2"

    fun applyPixelation(source: Bitmap, pixelSize: Int = DEFAULT_PIXEL_SIZE): Bitmap {
        val w = source.width
        val h = source.height
        val smallW = maxOf(w / pixelSize, 1)
        val smallH = maxOf(h / pixelSize, 1)
        val small = Bitmap.createScaledBitmap(source, smallW, smallH, false)
        val pixelated = Bitmap.createScaledBitmap(small, w, h, false)
        small.recycle()
        return pixelated
    }

    fun applyStackBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(maxOf(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        i = 0
        while (i < 256 * divsum) {
            dv[i] = i / divsum
            i++
        }

        yi = 0
        yw = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        y = 0
        while (y < h) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            i = -radius
            while (i <= radius) {
                p = pix[yi + minOf(wm, maxOf(i, 0))]
                sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - kotlin.math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            stackpointer = radius

            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vmin[x] = minOf(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
                x++
            }
            yw += w
            y++
        }

        x = 0
        while (x < w) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = maxOf(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - kotlin.math.abs(i)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i < hm) {
                    yp += w
                }
                i++
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < h) {
                pix[yi] = (0xff000000.toInt() and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vmin[y] = minOf(y + r1, hm) * w
                }
                p = x + vmin[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
                y++
            }
            x++
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pix, 0, w, 0, 0, w, h)
        return result
    }

    fun createLogoPlaceholder(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.parseColor(PLACEHOLDER_BG_COLOR))

        val minDimension = minOf(width, height)
        val logoSize = minOf(minDimension * 0.35f, 80f)
        val centerX = width / 2f
        val centerY = height / 2f

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(PLACEHOLDER_CIRCLE_COLOR)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, logoSize * 0.7f, circlePaint)

        val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(PLACEHOLDER_SHIELD_COLOR)
            style = Paint.Style.FILL
        }

        val shieldPath = Path().apply {
            val shieldWidth = logoSize * 0.5f
            val shieldHeight = logoSize * 0.6f
            val left = centerX - shieldWidth / 2
            val top = centerY - shieldHeight / 2
            val right = centerX + shieldWidth / 2
            val bottom = centerY + shieldHeight / 2

            moveTo(centerX, top)
            lineTo(right, top + shieldHeight * 0.12f)
            lineTo(right, top + shieldHeight * 0.55f)
            quadTo(right, bottom - shieldHeight * 0.08f, centerX, bottom)
            quadTo(left, bottom - shieldHeight * 0.08f, left, top + shieldHeight * 0.55f)
            lineTo(left, top + shieldHeight * 0.12f)
            close()
        }
        canvas.drawPath(shieldPath, shieldPaint)

        val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = logoSize * 0.08f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val checkPath = Path().apply {
            val checkSize = logoSize * 0.18f
            moveTo(centerX - checkSize, centerY)
            lineTo(centerX - checkSize * 0.3f, centerY + checkSize * 0.7f)
            lineTo(centerX + checkSize, centerY - checkSize * 0.5f)
        }
        canvas.drawPath(checkPath, checkPaint)

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    fun applyFacePixelation(source: Bitmap, pixelSize: Int = 24): Bitmap {
        val w = source.width
        val h = source.height
        val mutableBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        mutableBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (py in 0 until h step pixelSize) {
            for (px in 0 until w step pixelSize) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0

                val endY = minOf(py + pixelSize, h)
                val endX = minOf(px + pixelSize, w)

                for (y in py until endY) {
                    for (x in px until endX) {
                        val pixel = pixels[y * w + x]
                        rSum += (pixel shr 16) and 0xFF
                        gSum += (pixel shr 8) and 0xFF
                        bSum += pixel and 0xFF
                        count++
                    }
                }

                val avgColor = (0xFF shl 24) or
                        ((rSum / count) shl 16) or
                        ((gSum / count) shl 8) or
                        (bSum / count)

                for (y in py until endY) {
                    for (x in px until endX) {
                        pixels[y * w + x] = avgColor
                    }
                }
            }
        }

        mutableBitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return mutableBitmap
    }
}
