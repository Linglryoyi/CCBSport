package com.ccbsport.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun DataManagement(
    records: List<Workout>, active: Boolean, merge: (List<Workout>) -> Int?, clear: () -> Unit
) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableIntStateOf(0) }
    var count by rememberSaveable { mutableIntStateOf(0) }
    var clearing by rememberSaveable { mutableStateOf(false) }

    fun transfer(failure: Int, block: suspend () -> Unit) {
        busy = true
        status = 0
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                status = failure
            } finally {
                busy = false
            }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) transfer(R.string.export_failed) {
            val snapshot = records
            withContext(Dispatchers.IO) {
                val file = requireNotNull(resolver.openOutputStream(uri, "wt"))
                file.bufferedWriter(Charsets.UTF_8).use { it.write(WorkoutJson.export(snapshot)) }
            }
            status = R.string.export_done
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) transfer(R.string.import_failed) {
            val incoming = withContext(Dispatchers.IO) {
                requireNotNull(resolver.openInputStream(uri)).bufferedReader(Charsets.UTF_8).use {
                    WorkoutJson.import(it.readText())
                }
            }
            val added = merge(incoming)
            if (added != null) {
                count = added
                status = R.string.import_done
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t(R.string.history_heading, records.size), style = MaterialTheme.typography.headlineSmall)
        Text(t(R.string.backup_hint), style = MaterialTheme.typography.bodyMedium)
        SettingsRow(t(R.string.export_json), t(R.string.export_hint), R.drawable.history, true, false, enabled = !busy) {
            export.launch("CCBSport-${LocalDate.now()}.json")
        }
        SettingsRow(t(R.string.import_json), t(R.string.import_hint), R.drawable.add, false, true, enabled = !busy) {
            import.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status != 0) Text(if (status == R.string.import_done) t(status, count) else t(status),
            style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        OutlinedButton(onClick = { clearing = true }, enabled = !busy && !active, modifier = Modifier.fillMaxWidth()) {
            Text(t(R.string.clear_data), color = if (busy || active) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error)
        }
        if (active) Text(t(R.string.finish_before_clear), style = MaterialTheme.typography.bodySmall)
    }
    if (clearing) AlertDialog(
        onDismissRequest = { clearing = false },
        title = { Text(t(R.string.clear_data)) },
        text = { Text(t(R.string.clear_confirm)) },
        dismissButton = { TextButton(onClick = { clearing = false }) { Text(t(R.string.cancel)) } },
        confirmButton = {
            TextButton(enabled = !active && !busy, onClick = { clearing = false; clear() }) {
                Text(t(R.string.clear_action), color = MaterialTheme.colorScheme.error)
            }
        }
    )
}
