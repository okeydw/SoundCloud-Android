package com.scd.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SpeedPitchPanel(modifier: Modifier = Modifier) {
    val guest = Together.isGuest
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.speed_title), Modifier.weight(1f), fontWeight = FontWeight.Medium)
            if (SpeedPitch.active) {
                TextButton(onClick = {
                    if (!guest) Prefs.changePlaybackRate(SpeedPitch.RATE_DEFAULT)
                    Prefs.changePitchSemitones(0f)
                }) { Text(stringResource(R.string.color_reset)) }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.speed_label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(SpeedPitch.rateLabel(Prefs.playbackRate), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        val rateSteps = ((SpeedPitch.RATE_MAX - SpeedPitch.RATE_MIN) / SpeedPitch.RATE_STEP).roundToInt() - 1
        Slider(
            value = Prefs.playbackRate,
            onValueChange = { Prefs.changePlaybackRate(it) },
            valueRange = SpeedPitch.RATE_MIN..SpeedPitch.RATE_MAX,
            steps = rateSteps,
            enabled = !guest,
        )
        if (guest) {
            Text(
                stringResource(R.string.speed_guest_locked),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(8.dp))
        val follow = Prefs.pitchMode != SpeedPitch.MODE_MANUAL
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pitch_follow), style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
                checked = follow,
                onCheckedChange = { on -> Prefs.changePitchMode(if (on) SpeedPitch.MODE_AUTO else SpeedPitch.MODE_MANUAL) },
            )
        }

        val semitones = SpeedPitch.effectiveSemitones(Prefs.playbackRate, Prefs.pitchMode, Prefs.pitchSemitones)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pitch_label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.pitch_semitones, SpeedPitch.semitoneLabel(semitones)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        val semiSteps = ((SpeedPitch.SEMI_MAX - SpeedPitch.SEMI_MIN) / SpeedPitch.SEMI_STEP).roundToInt() - 1
        Slider(
            value = semitones,
            onValueChange = { Prefs.changePitchSemitones(it) },
            valueRange = SpeedPitch.SEMI_MIN..SpeedPitch.SEMI_MAX,
            steps = semiSteps,
            enabled = !follow,
        )
    }
}
