package com.scd.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import java.io.ByteArrayOutputStream

object NoteArtwork {

    private const val SIZE = 512
    private const val BACKGROUND = 0xFF2A2A2A.toInt()
    private const val TINT = 0xFFBDBDBD.toInt()

    @Volatile
    var bytes: ByteArray? = null
        private set

    fun init(context: Context) {
        if (bytes != null) return
        bytes = runCatching { render(context) }.getOrNull()
    }

    private fun render(context: Context): ByteArray {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)

        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_music)
            ?.mutate()
            ?: return emptyPng(bitmap)
        DrawableCompat.setTint(drawable, TINT)

        val inset = SIZE / 4
        drawable.setBounds(inset, inset, SIZE - inset, SIZE - inset)
        drawable.draw(canvas)

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun emptyPng(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }
}
