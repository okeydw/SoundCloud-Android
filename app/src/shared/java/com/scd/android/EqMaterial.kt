package com.scd.android

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerPanel() {
    val enabled = Prefs.eqEnabled
    val gains = Prefs.eqGains
    var saving by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val sub = MaterialTheme.colorScheme.onSurfaceVariant

    fun edit(block: () -> Unit) {
        block()
        if (!Prefs.eqEnabled) EqPresets.setEnabled(true)
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.equalizer), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(eqPresetName(Prefs.eqPresetId), style = MaterialTheme.typography.bodySmall, color = sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Switch(checked = enabled, onCheckedChange = { EqPresets.setEnabled(it) })
    }
    Spacer(Modifier.height(8.dp))
    Column(Modifier.alpha(if (enabled) 1f else 0.55f)) {
        val auto = Prefs.eqPreamp == null
        val shown = EqPresets.shownPreamp()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.eq_preamp),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(R.string.eq_db, EqCurve.signed(shown)), style = MaterialTheme.typography.bodySmall, color = sub, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = auto,
                onClick = { edit { EqPresets.setPreamp(if (auto) shown else null) } },
                label = { Text(stringResource(R.string.eq_auto), maxLines = 1) },
            )
        }
        Slider(
            value = shown.toFloat(),
            onValueChange = { v -> edit { EqPresets.setPreamp(v.toDouble()) } },
            valueRange = EqCurve.PREAMP_MIN.toFloat()..EqCurve.PREAMP_MAX.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EqPresets.builtin.forEach { preset ->
            FilterChip(
                selected = Prefs.eqPresetId == preset.id,
                onClick = { edit { EqPresets.select(preset.id) } },
                label = { Text(eqPresetName(preset.id), maxLines = 1) },
            )
        }
        if (Prefs.eqPresetId == EqPresets.CUSTOM) {
            FilterChip(selected = true, onClick = { saving = true }, label = { Text(stringResource(R.string.eq_custom), maxLines = 1) })
        }
    }
    val mine = EqPresets.custom
    if (mine.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.eq_my_presets), style = MaterialTheme.typography.labelMedium, color = sub)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            mine.forEach { preset ->
                FilterChip(
                    selected = Prefs.eqPresetId == preset.id,
                    onClick = { edit { EqPresets.select(preset.id) } },
                    label = { Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp)) },
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Column(Modifier.alpha(if (enabled) 1f else 0.55f)) {
        CappedFontScale(max = 1.3f) {
            for (band in 0 until EqCurve.BANDS) {
                val level = gains.getOrElse(band) { 0.0 }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        EqCurve.LABELS[band],
                        style = MaterialTheme.typography.bodySmall,
                        color = sub,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.width(40.dp),
                    )
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { v -> edit { EqPresets.setBand(band, v.toDouble()) } },
                        valueRange = -EqCurve.MAX_GAIN.toFloat()..EqCurve.MAX_GAIN.toFloat(),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        EqCurve.signed(level),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = sub,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(52.dp),
                    )
                }
            }
        }
    }
    val active = EqPresets.customById(Prefs.eqPresetId)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        TextButton(onClick = { EqPresets.reset() }) { Text(stringResource(R.string.color_reset), maxLines = 1) }
        TextButton(onClick = { saving = true }) { Text(stringResource(R.string.eq_save_preset), maxLines = 1) }
        TextButton(onClick = { importing = true }) { Text(stringResource(R.string.eq_import), maxLines = 1) }
        TextButton(onClick = { exporting = true }) { Text(stringResource(R.string.eq_export), maxLines = 1) }
        if (active != null) {
            TextButton(onClick = { EqPresets.deleteCustom(active.id) }) {
                Text(stringResource(R.string.eq_delete_preset), color = MaterialTheme.colorScheme.error, maxLines = 1)
            }
        }
    }

    if (saving) EqSaveMaterial(active?.name.orEmpty()) { saving = false }
    if (importing) EqImportMaterial { importing = false }
    if (exporting) EqExportMaterial { exporting = false }
}

@Composable
private fun EqSaveMaterial(initial: String, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    val name = value.trim()
    val replaces = EqPresets.custom.any { it.name.equals(name, ignoreCase = true) }
    val blocked = EqPresets.limitReached && !replaces
    AppDialog(onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text(stringResource(R.string.eq_save_preset), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(EqPresets.NAME_MAX) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.eq_preset_name), maxLines = 1) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (blocked) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.eq_preset_limit, EqPresets.LIMIT), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(enabled = name.isNotEmpty() && !blocked, onClick = { if (EqPresets.saveCustom(value)) onDismiss() }) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqImportMaterial(onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf<EqImportOutcome?>(null) }
    val pick = rememberEqFilePicker { picked ->
        outcome = if (picked == null) EqImportOutcome.Unreadable else {
            text = picked
            eqImportText(picked)
        }
    }
    AppDialog(onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text(stringResource(R.string.eq_import), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.eq_import_hint)) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 180.dp),
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = pick, label = { Text(stringResource(R.string.eq_from_file), maxLines = 1) })
                EqImportReport(outcome)
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(enabled = text.isNotBlank(), onClick = { outcome = eqImportText(text) }) { Text(stringResource(R.string.eq_apply)) }
            }
        }
    }
}

@Composable
private fun EqImportReport(outcome: EqImportOutcome?) {
    val style = MaterialTheme.typography.bodySmall
    val modifier = Modifier.padding(vertical = 14.dp)
    when (outcome) {
        is EqImportOutcome.Done -> {
            Text(stringResource(R.string.eq_import_used, outcome.result.used), style = style, color = MaterialTheme.colorScheme.primary, modifier = modifier)
            if (outcome.result.deviation >= 0.5) {
                Text(stringResource(R.string.eq_import_deviation, EqCurve.fixed(outcome.result.deviation, 1)), style = style, modifier = modifier)
            }
            if (outcome.result.skipped.isNotEmpty()) {
                Text(stringResource(R.string.eq_import_skipped, outcome.result.skipped.size), style = style, color = MaterialTheme.colorScheme.error, modifier = modifier)
            }
        }
        EqImportOutcome.Empty -> Text(stringResource(R.string.eq_import_failed), style = style, color = MaterialTheme.colorScheme.error, modifier = modifier)
        EqImportOutcome.Unreadable -> Text(stringResource(R.string.eq_read_failed), style = style, color = MaterialTheme.colorScheme.error, modifier = modifier)
        null -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqExportMaterial(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var format by remember { mutableStateOf(EqFormat.APO) }
    var status by remember { mutableStateOf<Int?>(null) }
    val text = remember(format, Prefs.eqGains, Prefs.eqPreamp) { eqExportText(format) }
    val save = rememberEqFileSaver { ok -> status = if (ok) R.string.eq_saved else R.string.eq_export_failed }
    AppDialog(onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text(stringResource(R.string.eq_export), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = format == EqFormat.APO, onClick = { format = EqFormat.APO; status = null }, label = { Text(stringResource(R.string.eq_format_apo), maxLines = 1) })
                FilterChip(selected = format == EqFormat.GRAPHIC, onClick = { format = EqFormat.GRAPHIC; status = null }, label = { Text(stringResource(R.string.eq_format_graphic), maxLines = 1) })
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = {},
                readOnly = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 180.dp),
            )
            status?.let {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), maxLines = 1) }
                TextButton(onClick = { save(eqExportName(format), text) }) { Text(stringResource(R.string.eq_save_file), maxLines = 1) }
                Button(onClick = { status = if (eqCopy(context, text)) R.string.eq_copied else R.string.eq_export_failed }) {
                    Text(stringResource(R.string.eq_copy), maxLines = 1)
                }
            }
        }
    }
}
