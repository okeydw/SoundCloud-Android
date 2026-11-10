package com.scd.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

internal object WidgetArtwork {
    data class Surfaces(val tile: Bitmap, val wide: Bitmap)

    fun crop(source: Bitmap): Bitmap {
        val target = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        cover(Canvas(target), source, 320f, 320f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return target
    }

    fun surfaces(source: Bitmap?, accent: Int): Surfaces = Surfaces(
        surface(source, accent, false), surface(source, accent, true),
    )

    private fun surface(source: Bitmap?, accent: Int, wide: Boolean): Bitmap {
        val width = if (wide) 480 else 320
        val height = if (wide) 240 else 320
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val w = width.toFloat()
        val h = height.toFloat()
        val bounds = RectF(0f, 0f, w, h)
        canvas.drawColor(Color.rgb(17, 19, 28))
        if (source != null && !source.isRecycled) {
            val texture = if (wide) Bitmap.createScaledBitmap(source, 12, 12, true) else source
            paint.alpha = if (wide) 115 else 255
            cover(canvas, texture, w, h, paint)
            if (texture !== source) texture.recycle()
            paint.alpha = 255
        }
        paint.shader = RadialGradient(w * 0.16f, h * 0.12f, w * 0.85f,
            intArrayOf(alpha(accent, if (source == null) 150 else 55), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(bounds, paint)
        paint.shader = LinearGradient(0f, 0f, if (wide) w else 0f, h,
            intArrayOf(0x16101520, 0x70101520, 0xF20A0C14.toInt()),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(bounds, paint)
        if (!wide) {
            paint.shader = LinearGradient(0f, 0f, 0f, h * 0.55f,
                intArrayOf(0x880A0C14.toInt(), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawRect(bounds, paint)
        }
        if (source == null) {
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.4f
            paint.color = alpha(accent, 48)
            repeat(4) { i -> canvas.drawCircle(w * 0.68f, h * 0.32f, 34f + i * 28f, paint) }
            paint.style = Paint.Style.FILL
        }
        paint.shader = LinearGradient(0f, 0f, w, h * 0.55f,
            intArrayOf(0x18FFFFFF, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(bounds, paint)
        return out
    }

    private fun cover(canvas: Canvas, source: Bitmap, width: Float, height: Float, paint: Paint) {
        val scale = maxOf(width / source.width, height / source.height)
        val left = (width - source.width * scale) / 2f
        val top = (height - source.height * scale) / 2f
        canvas.drawBitmap(source, null, RectF(left, top, left + source.width * scale, top + source.height * scale), paint)
    }

    private fun alpha(color: Int, alpha: Int): Int = (color and 0x00ffffff) or (alpha shl 24)
}
