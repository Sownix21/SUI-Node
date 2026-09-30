package com.sonix21.suinode.ui.screens.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.core.DateTimeInput
import com.sonix21.suinode.core.Fmt
import com.sonix21.suinode.core.UiLocale
import com.sonix21.suinode.ui.glass.*
import java.time.Instant
import java.time.ZoneId

/** DatePicker's UTC day is a calendar selection, not the selected local midnight. */
@Composable
fun DateTimeField(label: String, unixSeconds: Long, onChange: (Long) -> Unit) {
    var stage by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableLongStateOf(0L) }
    val zone = ZoneId.systemDefault()
    val initial = runCatching { Instant.ofEpochSecond(unixSeconds.takeIf { it > 0 } ?: System.currentTimeMillis()/1000).atZone(zone) }
        .getOrNull()?.takeIf { it.year in 1900..2200 } ?: Instant.now().atZone(zone)
    val g = LocalGlass.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(UiLocale.text(label), color = g.textDim)
        GhostButton(if (unixSeconds > 0) Fmt.dateTime(unixSeconds) else "Choose date & time") { stage = 1 }
        Text(if (unixSeconds == 0L) "No expiry · Gregorian calendar · ${zone.id}" else "Gregorian calendar · ${zone.id}", color = g.textFaint)
        if (unixSeconds > 0) GhostButton("Clear expiry · unlimited") { onChange(0L) }
    }
    if (stage == 1) {
        val date = rememberDatePickerState(initialSelectedDateMillis = DateTimeInput.pickerDate(initial.toEpochSecond(), zone),
            yearRange = 1900..2200)
        DatePickerDialog(onDismissRequest = { stage = 0 }, confirmButton = {
            TextButton(enabled = date.selectedDateMillis != null, onClick = { selectedDate = requireNotNull(date.selectedDateMillis); stage = 2 }) { Text(UiLocale.text("Next")) }
        }, dismissButton = { TextButton(onClick = { stage = 0 }) { Text(UiLocale.text("Cancel")) } }) { DatePicker(date) }
    }
    if (stage == 2) {
        val time = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { stage = 0 }, containerColor = g.surface,
            title = { Text("Time · ${zone.id}") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeInput(time)
                    error?.let { Text(it, color = g.err) }
                }
            }, confirmButton = { TextButton(onClick = {
                runCatching { DateTimeInput.unix(selectedDate, time.hour, time.minute, zone) }
                    .onSuccess { onChange(it); JsonEditSignal.bump(); stage = 0 }
                    .onFailure { error = it.message }
            }) { Text(UiLocale.text("Apply")) } }, dismissButton = {
                TextButton(onClick = { stage = 0 }) { Text(UiLocale.text("Cancel")) }
            })
    }
}
