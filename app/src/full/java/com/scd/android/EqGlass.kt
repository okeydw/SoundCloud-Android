package com.scd.android

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassEqualizer(onClose: (() -> Unit)?) {
    val p = LocalGlass.current
    val boost = p.accent
    val cut = if (p.gradient) p.accent2 else Color(0xFF8B7CFF)
    val enabled = Prefs.eqEnabled
    val gains = Prefs.eqGains
    var focus by remember { mutableIntStateOf(-1) }
    var saving by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    fun edit(block: () -> Unit) {
        block()
        if (!Prefs.eqEnabled) EqPresets.setEnabled(true)
    }

    ModalHeader(if (onClose == null) R.drawable.ic_equalizer else null, stringResource(R.string.equalizer), onClose) {
        IconOrb(R.drawable.ic_power, stringResource(R.string.equalizer), size = 44.dp, iconSize = 18.dp, active = enabled) {
            EqPresets.setEnabled(!enabled)
        }
        IconOrb(R.drawable.ic_refresh, stringResource(R.string.color_reset), size = 44.dp, iconSize = 16.dp) {
            EqPresets.reset()
            focus = -1
        }
    }
    Spacer(Modifier.height(12.dp))
    val readout = if (focus in 0 until EqCurve.BANDS) {
        stringResource(R.string.eq_band_readout, EqCurve.FREQS[focus].toLong().toString(), EqCurve.signed(gains[focus]))
    } else {
        eqPresetName(Prefs.eqPresetId)
    }
    Text(
        readout,
        style = GlassType.mono,
        color = p.text2,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Column(Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.5f }) {
        val auto = Prefs.eqPreamp == null
        val shown = EqPresets.shownPreamp()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.eq_preamp),
                style = GlassType.rowTitle,
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.eq_db, EqCurve.signed(shown)), style = GlassType.mono, color = p.text2, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(8.dp))
            GlassChip(stringResource(R.string.eq_auto), active = auto) {
                edit { EqPresets.setPreamp(if (auto) shown else null) }
            }
        }
        Slider(
            value = shown.toFloat(),
            onValueChange = { v -> edit { EqPresets.setPreamp(v.toDouble()) } },
            valueRange = EqCurve.PREAMP_MIN.toFloat()..EqCurve.PREAMP_MAX.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = p.accent,
                activeTrackColor = p.accent,
                inactiveTrackColor = p.text.copy(alpha = 0.12f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        CappedFontScale {
            Row(Modifier.fillMaxWidth().height(230.dp)) {
                Column(
                    Modifier.width(30.dp).fillMaxHeight().padding(top = 2.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf("+12", "0", "-12").forEach {
                        Text(it, style = GlassType.mono.copy(fontSize = 9.sp), color = p.text3, maxLines = 1, softWrap = false)
                    }
                }
                for (band in 0 until EqCurve.BANDS) {
                    val level = gains.getOrElse(band) { 0.0 }
                    val color = when {
                        level > 0.0 -> boost
                        level < 0.0 -> cut
                        else -> p.text3
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        VerticalBand(
                            value = level,
                            color = color,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            onTouch = { focus = band },
                        ) { v -> edit { EqPresets.setBand(band, v) } }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            EqCurve.LABELS[band],
                            style = GlassType.mono.copy(fontSize = 9.sp),
                            color = if (focus == band) p.text else p.text3,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    Eyebrow(stringResource(R.string.eq_preset_title), color = p.text3)
    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EqPresets.builtin.forEach { preset ->
            GlassChip(eqPresetName(preset.id), active = Prefs.eqPresetId == preset.id) {
                edit { EqPresets.select(preset.id) }
                focus = -1
            }
        }
        if (Prefs.eqPresetId == EqPresets.CUSTOM) {
            GlassChip(stringResource(R.string.eq_custom), active = true) { saving = true }
        }
    }
    val mine = EqPresets.custom
    if (mine.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Eyebrow(stringResource(R.string.eq_my_presets), color = p.text3)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            mine.forEach { preset ->
                GlassChip(
                    preset.name,
                    icon = R.drawable.ic_heart_filled,
                    active = Prefs.eqPresetId == preset.id,
                ) {
                    edit { EqPresets.select(preset.id) }
                    focus = -1
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    val active = EqPresets.customById(Prefs.eqPresetId)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassChip(stringResource(R.string.eq_save_preset), icon = R.drawable.ic_plus) { saving = true }
        GlassChip(stringResource(R.string.eq_import), icon = R.drawable.ic_download) { importing = true }
        GlassChip(stringResource(R.string.eq_export), icon = R.drawable.ic_upload) { exporting = true }
        if (active != null) {
            GlassChip(stringResource(R.string.eq_delete_preset), icon = R.drawable.ic_trash) { EqPresets.deleteCustom(active.id) }
        }
    }

    if (saving) EqSaveDialog(initial = active?.name.orEmpty()) { saving = false }
    if (importing) EqImportDialog { importing = false }
    if (exporting) EqExportDialog { exporting = false }
}

@Composable
private fun VerticalBand(value: Double, color: Color, modifier: Modifier, onTouch: () -> Unit, onChange: (Double) -> Unit) {
    val p = LocalGlass.current
    val latest by rememberUpdatedState(onChange)
    val touch by rememberUpdatedState(onTouch)
    var dragY by remember { mutableFloatStateOf(Float.NaN) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val knobMax = with(density) { 8.dp.toPx() }
    fun knob(width: Float): Float = knobMax.coerceAtMost(width / 2.6f)
    fun valueAt(y: Float, h: Float, r: Float): Double {
        val usable = (h - 2f * r).coerceAtLeast(1f)
        val frac = ((y - r) / usable).coerceIn(0f, 1f)
        return EqCurve.MAX_GAIN - frac * 2.0 * EqCurve.MAX_GAIN
    }
    Box(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    touch()
                    latest(valueAt(o.y, size.height.toFloat(), knob(size.width.toFloat())))
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { o ->
                        touch()
                        dragY = o.y
                    },
                    onDragEnd = { dragY = Float.NaN },
                    onDragCancel = { dragY = Float.NaN },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        dragY = change.position.y
                        latest(valueAt(change.position.y, size.height.toFloat(), knob(size.width.toFloat())))
                    },
                )
            }
            .drawBehind {
                val cx = size.width / 2f
                val trackW = 3.dp.toPx()
                val r = knob(size.width)
                val usable = (size.height - 2f * r).coerceAtLeast(1f)
                val zeroY = r + usable / 2f
                val y = r + usable * ((EqCurve.MAX_GAIN - value) / (2.0 * EqCurve.MAX_GAIN)).toFloat()
                drawRoundRect(
                    p.text.copy(alpha = 0.10f),
                    topLeft = Offset(cx - trackW / 2f, r),
                    size = Size(trackW, usable),
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
                drawCircle(color.copy(alpha = 0.30f), radius = r * 1.6f, center = Offset(cx, y))
                drawCircle(color, radius = r, center = Offset(cx, y))
                drawCircle(Color.White.copy(alpha = 0.35f), radius = r, center = Offset(cx, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            },
    )
}

@Composable
private fun EqSaveDialog(initial: String, onDismiss: () -> Unit) {
    val p = LocalGlass.current
    var value by remember { mutableStateOf(initial) }
    val name = value.trim()
    val replaces = EqPresets.custom.any { it.name.equals(name, ignoreCase = true) }
    val blocked = EqPresets.limitReached && !replaces
    GlassDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.eq_save_preset),
        confirm = stringResource(R.string.save),
        confirmEnabled = name.isNotEmpty() && !blocked,
        icon = R.drawable.ic_plus,
        onConfirm = {
            if (EqPresets.saveCustom(value)) onDismiss()
        },
    ) {
        GlassTextField(value, { value = it.take(EqPresets.NAME_MAX) }, placeholder = stringResource(R.string.eq_preset_name))
        if (blocked) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.eq_preset_limit, EqPresets.LIMIT), style = GlassType.small, color = p.text2)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqImportDialog(onDismiss: () -> Unit) {
    val p = LocalGlass.current
    var text by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf<EqImportOutcome?>(null) }
    val pick = rememberEqFilePicker { picked ->
        outcome = if (picked == null) EqImportOutcome.Unreadable else {
            text = picked
            eqImportText(picked)
        }
    }
    GlassDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.eq_import),
        confirm = stringResource(R.string.eq_apply),
        confirmEnabled = text.isNotBlank(),
        icon = R.drawable.ic_download,
        onConfirm = { outcome = eqImportText(text) },
    ) {
        GlassTextArea(text, { text = it }, placeholder = stringResource(R.string.eq_import_hint))
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip(stringResource(R.string.eq_from_file), icon = R.drawable.ic_import, onClick = pick)
            when (val o = outcome) {
                is EqImportOutcome.Done -> {
                    GlassChip(stringResource(R.string.eq_import_used, o.result.used), icon = R.drawable.ic_check)
                    if (o.result.deviation >= 0.5) GlassChip(stringResource(R.string.eq_import_deviation, EqCurve.fixed(o.result.deviation, 1)))
                    if (o.result.skipped.isNotEmpty()) GlassChip(stringResource(R.string.eq_import_skipped, o.result.skipped.size), iconTint = p.text2)
                }
                EqImportOutcome.Empty -> GlassChip(stringResource(R.string.eq_import_failed), iconTint = p.text2)
                EqImportOutcome.Unreadable -> GlassChip(stringResource(R.string.eq_read_failed), iconTint = p.text2)
                null -> Unit
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqExportDialog(onDismiss: () -> Unit) {
    val p = LocalGlass.current
    val context = LocalContext.current
    var format by remember { mutableStateOf(EqFormat.APO) }
    var status by remember { mutableStateOf<Int?>(null) }
    val text = remember(format, Prefs.eqGains, Prefs.eqPreamp) { eqExportText(format) }
    val save = rememberEqFileSaver { ok -> status = if (ok) R.string.eq_saved else R.string.eq_export_failed }
    GlassDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.eq_export),
        confirm = stringResource(R.string.eq_copy),
        icon = R.drawable.ic_upload,
        onConfirm = { status = if (eqCopy(context, text)) R.string.eq_copied else R.string.eq_export_failed },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip(stringResource(R.string.eq_format_apo), active = format == EqFormat.APO) { format = EqFormat.APO; status = null }
            GlassChip(stringResource(R.string.eq_format_graphic), active = format == EqFormat.GRAPHIC) { format = EqFormat.GRAPHIC; status = null }
        }
        Spacer(Modifier.height(10.dp))
        GlassTextArea(text, {}, readOnly = true)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip(stringResource(R.string.eq_save_file), icon = R.drawable.ic_download) { save(eqExportName(format), text) }
            status?.let { GlassChip(stringResource(it), icon = R.drawable.ic_check, iconTint = p.text2) }
        }
    }
}

@Composable
private fun GlassTextArea(value: String, onChange: (String) -> Unit, placeholder: String? = null, readOnly: Boolean = false) {
    val p = LocalGlass.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(124.dp)
            .glass(RoundedCornerShape(16.dp), p, shadow = 0.dp, sheen = false)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty() && placeholder != null) {
            Text(placeholder, style = GlassType.small, color = p.text3, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            readOnly = readOnly,
            textStyle = GlassType.mono.copy(fontSize = 11.sp, lineHeight = 15.sp, color = p.text),
            cursorBrush = SolidColor(p.accent),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
