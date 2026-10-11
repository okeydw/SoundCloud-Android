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
    GlassModal(onDismiss) { GlassEqualizer(onClose = onDismiss) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassEqualizer(onClose: (() -> Unit)?) {
    val p = LocalGlass.current
    val info = AudioFx.info
    val boost = p.accent
    val cut = if (p.gradient) p.accent2 else Color(0xFF8B7CFF)
    ModalHeader(R.drawable.ic_equalizer, stringResource(R.string.equalizer), onClose) {
        IconOrb(
            R.drawable.ic_power,
            stringResource(R.string.equalizer),
            size = 44.dp,
            iconSize = 18.dp,
            active = Prefs.eqEnabled,
            enabled = info != null,
        ) { Prefs.changeEq(!Prefs.eqEnabled, Prefs.eqPreset, Prefs.eqBands) }
        if (info != null) {
            IconOrb(R.drawable.ic_refresh, stringResource(R.string.color_reset), size = 44.dp, iconSize = 16.dp) {
                Prefs.changeEq(Prefs.eqEnabled, -1, List(info.bands) { 0 }.joinToString(","))
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    if (info == null) {
        Text(stringResource(R.string.eq_unavailable), style = GlassType.body, color = p.text2)
        return
    }
    val levels = if (Prefs.eqPreset >= 0) {
        AudioFx.levels(Prefs.eqPreset)
    } else {
        val parsed = AudioFx.parseBands(Prefs.eqBands)
        List(info.bands) { parsed.getOrNull(it) ?: 0 }
    }
    val maxDb = info.maxLevel / 100
    val minDb = info.minLevel / 100
    Row(Modifier.fillMaxWidth().height(250.dp)) {
        Column(Modifier.width(30.dp).fillMaxHeight().padding(top = 24.dp, bottom = 26.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf("+$maxDb", "0", "$minDb").forEach {
                Text(it, style = GlassType.mono.copy(fontSize = 10.sp), color = p.text3)
            }
        }
        levels.forEachIndexed { band, level ->
            val hz = info.centerFreqsHz.getOrNull(band) ?: 0
            val label = if (hz >= 1000) "${hz / 1000}K" else "$hz"
            val color = when {
                level > 0 -> boost
                level < 0 -> cut
                else -> p.text3
            }
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (level == 0) "0.0" else "%+.1f".format(level / 100f),
                    style = GlassType.mono.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                    color = color,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                BandSlider(
                    level = level,
                    min = info.minLevel,
                    max = info.maxLevel,
                    color = color,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { v ->
                    val next = levels.toMutableList().also { it[band] = v }
                    Prefs.changeEq(true, -1, next.joinToString(","))
                }
                Spacer(Modifier.height(8.dp))
                Text(label, style = GlassType.mono.copy(fontSize = 10.sp), color = p.text3, maxLines = 1)
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    Eyebrow(stringResource(R.string.eq_preset), color = p.text3)
    Spacer(Modifier.height(10.dp))
    val activeUser = EqPresets.activeUser()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassChip(stringResource(R.string.eq_custom), active = Prefs.eqPreset < 0 && activeUser == null, onClick = { Prefs.changeEq(true, -1, levels.joinToString(",")) })
        EqPresets.user.forEach { preset ->
            GlassChip(preset.name, active = activeUser == preset, icon = R.drawable.ic_heart_filled, onClick = { EqPresets.use(preset) })
        }
        EqPresets.visibleSystem(info.presets).forEach { (i, name) ->
            GlassChip(name, active = Prefs.eqPreset == i, onClick = { Prefs.changeEq(true, i, Prefs.eqBands) })
        }
    }
    Spacer(Modifier.height(14.dp))
    var saving by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassChip(stringResource(R.string.eq_save_preset), icon = R.drawable.ic_plus, onClick = { saving = true })
        if (activeUser != null) {
            GlassChip(stringResource(R.string.eq_delete_preset), icon = R.drawable.ic_trash, onClick = { EqPresets.delete(activeUser.name) })
        } else if (Prefs.eqPreset >= 0) {
            GlassChip(stringResource(R.string.eq_delete_preset), icon = R.drawable.ic_trash, onClick = { EqPresets.hideSystem(Prefs.eqPreset) })
        }
        if (Prefs.eqHidden.isNotEmpty()) {
            GlassChip(stringResource(R.string.eq_restore_presets), icon = R.drawable.ic_refresh, onClick = { EqPresets.restoreSystem() })
        }
    }
    if (saving) {
        var value by remember { mutableStateOf(activeUser?.name ?: "") }
        GlassDialog(
            onDismiss = { saving = false },
            title = stringResource(R.string.eq_save_preset),
            confirm = stringResource(R.string.save),
            confirmEnabled = value.isNotBlank(),
            onConfirm = {
                EqPresets.save(value, levels.joinToString(","))
                saving = false
            },
        ) { GlassTextField(value, { value = it.take(32) }, placeholder = stringResource(R.string.eq_preset_name)) }
    }
}

@Composable
private fun BandSlider(level: Int, min: Int, max: Int, color: Color, modifier: Modifier, onChange: (Int) -> Unit) {
    val p = LocalGlass.current
    val latest by rememberUpdatedState(onChange)
    var drag by remember { mutableFloatStateOf(Float.NaN) }
    fun valueAt(y: Float, h: Float): Int {
        val frac = (y / h).coerceIn(0f, 1f)
        val raw = max - frac * (max - min)
        return ((raw / 50f).toInt() * 50).coerceIn(min, max)
    }
    Box(
        modifier
            .pointerInput(min, max) {
                detectTapGestures { o -> latest(valueAt(o.y, size.height.toFloat())) }
            }
            .pointerInput(min, max) {
                detectVerticalDragGestures(
                    onDragStart = { o -> drag = o.y },
                    onDragEnd = { drag = Float.NaN },
                    onDragCancel = { drag = Float.NaN },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        drag = change.position.y
                        latest(valueAt(change.position.y, size.height.toFloat()))
                    },
                )
            }
            .drawBehind {
                val cx = size.width / 2f
                val trackW = 3.dp.toPx()
                val range = (max - min).toFloat().coerceAtLeast(1f)
                val zeroY = size.height * (max / range)
                val y = size.height * ((max - level) / range)
                drawRoundRect(
                    p.text.copy(alpha = 0.10f),
                    topLeft = Offset(cx - trackW / 2f, 0f),
                    size = Size(trackW, size.height),
                    cornerRadius = CornerRadius(trackW),
                )
                val top = minOf(y, zeroY)
                val bottom = maxOf(y, zeroY)
                drawRoundRect(
                    Brush.verticalGradient(listOf(color, color.copy(alpha = 0.35f)), startY = top, endY = bottom),
                    topLeft = Offset(cx - trackW / 2f, top),
                    size = Size(trackW, (bottom - top).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius(trackW),
                )
                val r = 9.dp.toPx()
                drawCircle(color.copy(alpha = 0.30f), radius = r * 1.7f, center = Offset(cx, y))
                drawCircle(color, radius = r, center = Offset(cx, y))
                drawCircle(Color.White.copy(alpha = 0.35f), radius = r, center = Offset(cx, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            },
    )
}
