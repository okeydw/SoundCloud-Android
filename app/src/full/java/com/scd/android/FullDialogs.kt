package com.scd.android

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

val GlassDialogSkin = DialogSkin(frame = { content -> GlassModalFrame { content() } })

@Composable
fun GlassModalFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalGlass.current
    Column(
        modifier
            .fillMaxWidth()
            .softShadow(RoundedCornerShape(30.dp), 24.dp, Color.Black.copy(alpha = if (p.dark) 0.55f else 0.18f), 8.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(p.scrim)
            .glass(RoundedCornerShape(30.dp), p, shadow = 0.dp, tint = p.glow.copy(alpha = 0.08f)),
    ) {
        content()
    }
}

@Composable
fun ModalHeader(
    icon: Int?,
    title: String,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val p = LocalGlass.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Box(Modifier.size(44.dp).glass(RoundedCornerShape(14.dp), p, shadow = 0.dp), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, tint = p.text2, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Text(title, style = GlassType.section, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            actions()
            if (onClose != null) {
                IconOrb(R.drawable.ic_clear, stringResource(R.string.cd_close), size = 44.dp, iconSize = 16.dp, onClick = onClose)
            }
        }
    }
}

@Composable
fun GlassModal(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassModalFrame(Modifier.padding(horizontal = 14.dp).heightIn(max = 680.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp)) { content() }
        }
    }
}

@Composable
fun EqualizerModal(onDismiss: () -> Unit) {
    GlassModal(onDismiss) {
        GlassEqualizer(onClose = onDismiss)
        Spacer(Modifier.height(22.dp))
        SpeedPitchPanel()
    }
}

