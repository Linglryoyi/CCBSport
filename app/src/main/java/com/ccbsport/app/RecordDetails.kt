@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ccbsport.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import kotlin.random.Random

@Composable
fun DetailScreen(record: Workout, update: (Workout) -> Boolean, delete: () -> Unit) {
    val context = LocalContext.current
    var mapUnavailable by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    var edit by rememberSaveable(record.id) { mutableStateOf(false) }
    var confirm by rememberSaveable(record.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (record.sport == Sport.Outdoor && record.points.isNotEmpty()) {
            RouteMap(record.points, Modifier.fillMaxWidth().height(300.dp))
            OutlinedButton(onClick = {
                val finish = record.points.last()
                val point = "${finish.lat},${finish.lon}"
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$point?q=$point")))
                } catch (_: ActivityNotFoundException) {
                    mapUnavailable = true
                }
            }) {
                Glyph(R.drawable.map)
                Spacer(Modifier.width(8.dp))
                Text(t(R.string.open_map))
            }
            if (mapUnavailable) Text(t(R.string.no_map), color = colors.error)
        } else {
            Surface(color = colors.primaryContainer, contentColor = colors.onPrimaryContainer,
                shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp).heightIn(min = 128.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Glyph(record.sport.icon, modifier = Modifier.size(56.dp))
                    Text(if (record.sport == Sport.Outdoor) t(R.string.no_gps_route) else t(R.string.workout_saved),
                        style = type.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        Surface(color = colors.surfaceContainerLow, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(t(record.sport.label), Modifier.weight(1f), maxLines = 1, style = type.headlineSmall)
                    Text(dateLabel(record.date), style = type.bodyMedium, maxLines = 1, color = colors.onSurfaceVariant)
                }
                HorizontalDivider()
                if (record.meters > 0 || record.sport in listOf(Sport.Outdoor, Sport.Indoor)) {
                    Metric(t(if (record.estimatedMeters > 0) R.string.step_estimate_label else R.string.distance), "${km(record.meters)} km")
                }
                if (record.steps > 0) Metric(t(R.string.steps), record.steps.toString())
                Metric(t(R.string.duration), clock(record.seconds))
                if (record.meters > 0) Metric(t(R.string.pace), "${pace(record.seconds, record.meters)} / km")
                if (record.note.isNotBlank()) {
                    HorizontalDivider()
                    Text(record.note, style = type.bodyMedium)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { edit = true }, modifier = Modifier.weight(1f).height(56.dp)) {
                Glyph(R.drawable.edit); Spacer(Modifier.width(8.dp)); Text(t(R.string.edit))
            }
            OutlinedButton(onClick = { confirm = true }, modifier = Modifier.weight(1f).height(56.dp)) {
                Glyph(R.drawable.delete); Spacer(Modifier.width(8.dp)); Text(t(R.string.delete))
            }
        }
    }
    if (edit) RecordEditor(record, ending = false, dismiss = { edit = false }, onSave = {
        if (update(it)) {
            edit = false
            true
        } else false
    })
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(t(R.string.delete_question)) },
        text = { Text(t(R.string.delete_help)) },
        confirmButton = { TextButton(onClick = { confirm = false; delete() }) { Text(t(R.string.delete), color = colors.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(t(R.string.cancel)) } }
    )
}

@Composable
private fun Metric(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer, shape = CircleShape) {
            Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
        }
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun RecordEditor(record: Workout, ending: Boolean, dismiss: () -> Unit, discard: () -> Unit = {}, onSave: (Workout) -> Boolean) {
    var distance by rememberSaveable(record.id) { mutableStateOf(if (record.meters > 0) km(record.meters) else "") }
    var note by rememberSaveable(record.id) { mutableStateOf(record.note) }
    var invalidDistance by rememberSaveable(record.id) { mutableStateOf(false) }
    var discardConfirm by rememberSaveable(record.id) { mutableStateOf(false) }
    val canEnterDistance = record.sport != Sport.Fitness && record.points.isEmpty()
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (ending) t(R.string.save_question) else t(R.string.edit_workout)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${t(record.sport.label)} · ${clock(record.seconds)}" +
                    if (record.points.isNotEmpty()) " · ${km(record.meters)} km" else "")
                if (canEnterDistance) Field(distance, { distance = it.take(10) }, t(R.string.distance_optional), KeyboardType.Decimal)
                Field(note, { note = it.take(120) }, t(R.string.note_optional))
                if (invalidDistance) Text(t(R.string.distance_error), color = colors.error)
                if (ending) TextButton(onClick = { discardConfirm = true }) { Text(t(R.string.discard_workout), color = colors.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val input = distance.trim().replace(',', '.')
                val value = if (input.isBlank()) 0.0 else input.toDoubleOrNull()
                if (canEnterDistance && (value == null || value !in 0.0..1000.0)) {
                    invalidDistance = true
                } else {
                    val unchanged = !canEnterDistance || input == km(record.meters)
                    onSave(record.copy(
                        meters = if (unchanged) record.meters else (value ?: 0.0) * 1000,
                        estimatedMeters = if (unchanged) record.estimatedMeters else 0.0,
                        note = note.trim()
                    ))
                }
            }) { Text(t(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(if (ending) t(R.string.resume) else t(R.string.cancel)) } }
    )
    if (discardConfirm) AlertDialog(
        onDismissRequest = { discardConfirm = false },
        title = { Text(t(R.string.discard_question)) },
        text = { Text(t(R.string.discard_help)) },
        confirmButton = { TextButton(onClick = discard) { Text(t(R.string.discard), color = colors.error) } },
        dismissButton = { TextButton(onClick = { discardConfirm = false }) { Text(t(R.string.cancel)) } }
    )
}

@Composable
fun GenerateScreen(add: (List<Workout>) -> Unit) {
    val now = YearMonth.now()
    var firstMonth by rememberSaveable { mutableStateOf(now.toString()) }
    var lastMonth by rememberSaveable { mutableStateOf(now.toString()) }
    var picking by rememberSaveable { mutableIntStateOf(0) }
    val first = YearMonth.parse(firstMonth)
    val last = YearMonth.parse(lastMonth)
    var count by rememberSaveable { mutableStateOf("6") }
    var minimum by rememberSaveable { mutableStateOf("2") }
    var maximum by rememberSaveable { mutableStateOf("4") }
    var minutes by rememberSaveable { mutableStateOf("22") }
    var error by rememberSaveable { mutableIntStateOf(0) }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t(R.string.generate_options), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MonthField(t(R.string.start_month), first, Modifier.weight(1f)) { picking = 1 }
            MonthField(t(R.string.end_month), last, Modifier.weight(1f)) { picking = 2 }
        }
        Text(t(R.string.month_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Field(count, { count = it.take(4) }, t(R.string.run_count), KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Field(minimum, { minimum = it.take(8) }, t(R.string.min_distance), KeyboardType.Decimal) }
            Box(Modifier.weight(1f)) { Field(maximum, { maximum = it.take(8) }, t(R.string.max_distance), KeyboardType.Decimal) }
        }
        Field(minutes, { minutes = it.take(4) }, t(R.string.run_minutes), KeyboardType.Number)
        if (error != 0) Text(t(error), color = colors.error)
        Button(modifier = Modifier.align(Alignment.End).height(56.dp), onClick = {
            val n = count.toIntOrNull()
            val min = minimum.trim().replace(',', '.').toDoubleOrNull()
            val max = maximum.trim().replace(',', '.').toDoubleOrNull()
            val time = minutes.toIntOrNull()
            error = when {
                n == null || n !in 1..300 -> R.string.count_error
                min == null || max == null || min !in .1..100.0 || max !in min..100.0 -> R.string.range_error
                time == null || time !in 1..600 -> R.string.time_error
                else -> 0
            }
            if (error == 0 && n != null && min != null && max != null && time != null) {
                val zone = ZoneId.systemDefault()
                val start = first.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val endDate = minOf(last.atEndOfMonth(), LocalDate.now())
                val end = minOf(endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
                add(List(n) { i ->
                    val date = start + ((end - start) * (i + Random.nextDouble()) / n).toLong()
                    val meters = (min + (max - min) * Random.nextDouble()) * 1000
                    Workout(Sport.Outdoor, date, (time * 60 * (0.85 + Random.nextDouble() * .3)).toLong(), meters)
                })
            }
        }) { Glyph(R.drawable.add); Spacer(Modifier.width(8.dp)); Text(t(R.string.generate_records)) }
    }
    if (picking != 0) {
        val isStart = picking == 1
        MonthPicker(
            title = t(if (isStart) R.string.start_month else R.string.end_month),
            selected = if (isStart) first else last,
            earliest = now.minusYears(10), latest = now, dismiss = { picking = 0 }
        ) { month ->
            if (isStart) {
                firstMonth = month.toString()
                if (month > last) lastMonth = firstMonth
            } else {
                lastMonth = month.toString()
                if (month < first) firstMonth = lastMonth
            }
            picking = 0
        }
    }
}
