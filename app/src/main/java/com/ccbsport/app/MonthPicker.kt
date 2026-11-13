package com.ccbsport.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@Composable
fun MonthField(label: String, selected: YearMonth, modifier: Modifier = Modifier, open: () -> Unit) {
    val locale = LocalAppLanguage.current.locale
    val pattern = when (locale.language) {
        "zh", "ja" -> "uuuu年M月"
        "ko" -> "uuuu년 M월"
        else -> "MMM uuuu"
    }
    OutlinedButton(onClick = open, modifier = modifier.heightIn(min = 64.dp), shape = MaterialTheme.shapes.large) {
        Glyph(R.drawable.calendar_month)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(selected.format(DateTimeFormatter.ofPattern(pattern, locale)))
        }
    }
}

@Composable
fun MonthPicker(
    title: String, selected: YearMonth, earliest: YearMonth, latest: YearMonth,
    dismiss: () -> Unit, choose: (YearMonth) -> Unit
) {
    var year by rememberSaveable { mutableIntStateOf(selected.year) }
    val locale = LocalAppLanguage.current.locale
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(enabled = year > earliest.year, onClick = { year-- }) {
                        Glyph(R.drawable.arrow_back, t(R.string.previous_year))
                    }
                    Text(year.toString(), style = MaterialTheme.typography.titleLarge)
                    IconButton(enabled = year < latest.year, onClick = { year++ }) {
                        Glyph(R.drawable.chevron_right, t(R.string.next_year))
                    }
                }
                repeat(4) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) { col ->
                            val month = YearMonth.of(year, row * 3 + col + 1)
                            FilterChip(
                                selected = month == selected, enabled = month in earliest..latest,
                                onClick = { choose(month) }, modifier = Modifier.weight(1f),
                                label = { Text(month.month.getDisplayName(TextStyle.SHORT, locale), maxLines = 1) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(t(R.string.cancel)) } }
    )
}
