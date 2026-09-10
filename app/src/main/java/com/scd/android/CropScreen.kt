package com.scd.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

@Composable
fun CropDialog(source: Uri, onDismiss: () -> Unit, onResult: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var frameW by remember { mutableStateOf(1) }
    var frameH by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }

    val ratio = remember {
        val metrics = ctx.resources.displayMetrics
        metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat()
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                stringResource(R.string.crop_hint),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                        .clipToBounds()
                        .border(2.dp, Color.White.copy(alpha = 0.85f))
                        .onSizeChanged { frameW = it.width; frameH = it.height }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        },
                ) {
                    AsyncImage(
                        model = source,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { if (!busy) onDismiss() }) {
                    Text(stringResource(R.string.cancel), color = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val ok = ImageCropper.crop(
                                ctx, source, scale, offsetX, offsetY, frameW, frameH,
                            )
                            busy = false
                            onResult(ok)
                        }
                    },
                ) { Text(stringResource(if (busy) R.string.saving else R.string.crop_apply)) }
            }
        }
    }
}

object ImageCropper {

    suspend fun crop(
        context: Context,
        source: Uri,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        frameW: Int,
        frameH: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decode(context, source) ?: return@runCatching false
            if (frameW <= 0 || frameH <= 0) return@runCatching false

            val outW = frameW.coerceAtMost(1920)
            val outH = (outW.toFloat() * frameH / frameW).toInt().coerceAtLeast(1)
            val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val ratio = outW.toFloat() / frameW

            // ContentScale.Crop: картинка растянута так, чтобы покрыть рамку целиком
            val cover = max(frameW.toFloat() / bitmap.width, frameH.toFloat() / bitmap.height)
            val drawn = cover * scale * ratio
            val matrix = Matrix().apply {
                postScale(drawn, drawn)
                postTranslate(
                    outW / 2f - bitmap.width * drawn / 2f + offsetX * ratio,
                    outH / 2f - bitmap.height * drawn / 2f + offsetY * ratio,
                )
            }
            canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            bitmap.recycle()

            val saved = BackgroundImage.save(context, result)
            result.recycle()
            saved
        }.getOrDefault(false)
    }

    private fun decode(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / sample > 2560) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }
}
