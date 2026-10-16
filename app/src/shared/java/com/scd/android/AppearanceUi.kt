package com.scd.android

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object Motion {
    const val duration = 320

    fun <S> overlay(): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        (fadeIn(tween(duration)) + scaleIn(tween(duration), initialScale = 0.97f)) togetherWith
            (fadeOut(tween(duration * 2 / 3)) + scaleOut(tween(duration), targetScale = 1.01f))
    }
}

@Composable
private fun IconPreview(id: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.width(76.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(shape)
                .background(if (id == LauncherIcons.BLUE) Color(0xFF0048FF) else Color.Transparent)
                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, shape),
        ) {
            when (id) {
                LauncherIcons.VIOLET -> {
                    Image(painterResource(R.drawable.ic_launcher_full_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Image(painterResource(R.drawable.ic_launcher_full_foreground), null, Modifier.fillMaxSize())
                }
                LauncherIcons.ORANGE -> {
                    Image(painterResource(R.drawable.ic_launcher_orange_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Image(painterResource(R.drawable.ic_launcher_orange_foreground), null, Modifier.fillMaxSize())
                }
                else -> Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(
                when (id) {
                    LauncherIcons.VIOLET -> R.string.icon_violet
                    LauncherIcons.ORANGE -> R.string.icon_orange
                    else -> R.string.icon_blue
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
fun LauncherIconDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(LauncherIcons.current(context)) }
    var source by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) source = uri }
    val label = stringResource(R.string.app_name)
    source?.let { uri ->
        CropDialog(
            source = uri,
            onDismiss = { source = null },
            aspect = 1f,
            save = { bmp -> LauncherIcons.pinCustom(context, bmp, label) },
        ) { ok ->
            source = null
            android.widget.Toast.makeText(
                context,
                context.getString(if (ok) R.string.icon_pinned else R.string.icon_pin_failed),
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
        return
    }
    AppDialog(onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text(stringResource(R.string.icon_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.icon_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                LauncherIcons.all.forEach { id ->
                    IconPreview(id, current == id) {
                        if (LauncherIcons.apply(context, id)) {
                            current = id
                            android.widget.Toast.makeText(context, context.getString(R.string.icon_changed), android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (LauncherIcons.canPin(context)) {
                OutlinedButton(onClick = { runCatching { picker.launch("image/*") } }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.icon_custom))
                }
                Text(
                    stringResource(R.string.icon_custom_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cd_close)) }
            }
        }
    }
}
