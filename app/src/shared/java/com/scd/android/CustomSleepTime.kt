package com.scd.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource

@Composable
fun CustomSleepTime(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var input by rememberSaveable { mutableStateOf("") }
    val minutes = input.toIntOrNull()?.takeIf { it in 1..1440 }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = { if (it.length <= 4 && it.all { digit -> digit in '0'..'9' }) input = it },
            label = { Text(stringResource(R.string.sleep_custom_minutes)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = input.isNotEmpty() && minutes == null,
            modifier = Modifier.weight(1f),
        )
        TextButton(enabled = minutes != null, onClick = {
            minutes?.let { SleepTimer.start(it); onDismiss() }
        }) { Text(stringResource(R.string.sleep_custom_start)) }
    }
}
