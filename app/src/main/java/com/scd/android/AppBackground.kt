package com.scd.android

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BlurSupport {
    val available: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

object BackgroundImage {

    private fun dir(context: Context) = File(context.applicationContext.filesDir, "background")

    fun fileFor(context: Context, stamp: Long) = File(dir(context), "bg_$stamp.jpg")

    suspend fun import(context: Context, source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val folder = dir(context).apply { mkdirs() }
            val stamp = System.currentTimeMillis()
            val target = File(folder, "bg_$stamp.jpg")
            context.contentResolver.openInputStream(source).use { input ->
                if (input == null) return@runCatching false
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.length() <= 0L) {
                target.delete()
                return@runCatching false
            }
            folder.listFiles()?.forEach { if (it != target) it.delete() }
            Prefs.changeBackgroundImage(target.absolutePath)
            true
        }.getOrDefault(false)
    }

    fun save(context: Context, bitmap: android.graphics.Bitmap): Boolean = runCatching {
        val folder = dir(context).apply { mkdirs() }
        val target = File(folder, "bg_${System.currentTimeMillis()}.jpg")
        target.outputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
        }
        if (target.length() <= 0L) {
            target.delete()
            return@runCatching false
        }
        folder.listFiles()?.forEach { if (it != target) it.delete() }
        Prefs.changeBackgroundImage(target.absolutePath)
        true
    }.getOrDefault(false)

    fun saveBytes(context: Context, bytes: ByteArray): Boolean = runCatching {
        if (bytes.isEmpty()) return@runCatching false
        val folder = dir(context).apply { mkdirs() }
        val target = File(folder, "bg_${System.currentTimeMillis()}.jpg")
        target.writeBytes(bytes)
        folder.listFiles()?.forEach { if (it != target) it.delete() }
        Prefs.changeBackgroundImage(target.absolutePath)
        true
    }.getOrDefault(false)

    fun clear(context: Context) {
        runCatching { dir(context).deleteRecursively() }
        Prefs.changeBackgroundImage(null)
    }
}

@Composable
fun AppBackground() {
    val path = Prefs.backgroundImage ?: return
    val blur = Prefs.backgroundBlur && BlurSupport.available
    val veil = MaterialTheme.colorScheme.background
    val dim = Prefs.backgroundDim.coerceIn(0f, 0.95f)

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (blur && Prefs.backgroundBlurRadius > 0f) {
                        Modifier.blur(Prefs.backgroundBlurRadius.dp)
                    } else {
                        Modifier
                    }
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to veil.copy(alpha = dim),
                        1f to veil.copy(alpha = (dim + 0.12f).coerceAtMost(1f)),
                    )
                )
        )
    }
}
