package com.ccbsport.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun OfflineMapSetup(finish: () -> Unit) {
    val context = LocalContext.current
    val maps by remember(context) { OfflineMaps.get(context) }.maps.collectAsState()
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Bubble(R.drawable.map)
                Spacer(Modifier.height(12.dp))
                Text(t(R.string.offline_setup_title), style = MaterialTheme.typography.headlineSmall)
                Text(t(R.string.offline_setup_hint), style = MaterialTheme.typography.bodyMedium)
            }
            OfflineMapScreen(Modifier.weight(1f))
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                if (maps.isEmpty()) {
                    FilledTonalButton(onClick = finish) { Text(t(R.string.skip_setup)) }
                } else {
                    Button(onClick = finish) { Text(t(R.string.continue_label)) }
                }
            }
        }
    }
}

@Composable
fun OfflineMapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val library = remember(context) { OfflineMaps.get(context) }
    val maps by library.maps.collectAsState()
    val cities by library.cities.collectAsState()
    val transfer by library.transfer.collectAsState()
    val error by library.error.collectAsState()
    var adding by rememberSaveable { mutableStateOf(false) }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val catalog = remember(library) { library.catalog }
    val city = catalog.firstOrNull { it.id == chosen }
    val map = maps.firstOrNull { it.id == deleting }
    val busy = transfer != null
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) library.import(it)
    }
    LazyColumn(
        modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(t(R.string.offline_intro), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { adding = true }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(t(R.string.offline_add_city))
                }
                OutlinedButton(onClick = { importFile.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(t(R.string.offline_import))
                }
            }
        }
        transfer?.let { active ->
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(active.label, style = MaterialTheme.typography.titleMedium)
                        if (active.total > 0) {
                            LinearProgressIndicator(
                                progress = { (active.received.toDouble() / active.total).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(t(R.string.offline_progress, active.received / 1048576.0, active.total / 1048576.0))
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(t(R.string.offline_received, active.received / 1048576.0))
                        }
                        TextButton(onClick = library::cancel, modifier = Modifier.align(Alignment.End)) { Text(t(R.string.cancel)) }
                    }
                }
            }
        }
        item { Text(t(R.string.offline_saved_cities), style = MaterialTheme.typography.titleLarge) }
        val favorites = catalog.filter { it.id in cities }
        if (favorites.isEmpty()) item { Text(t(R.string.offline_no_cities)) }
        items(favorites, key = { it.id }) { saved ->
            val hasPackage = maps.any { it.id == saved.packageId }
            ListItem(
                headlineContent = { Text(saved.name) },
                supportingContent = { Text(saved.region + " · " + t(if (hasPackage) R.string.offline_ready else R.string.offline_missing)) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!hasPackage) TextButton(onClick = { chosen = saved.id }, enabled = !busy) { Text(t(R.string.offline_download)) }
                        IconButton(onClick = { library.removeCity(saved) }, enabled = !busy) {
                            Glyph(R.drawable.delete, t(R.string.offline_remove_city))
                        }
                    }
                }
            )
        }
        item { Text(t(R.string.offline_packages), style = MaterialTheme.typography.titleLarge) }
        if (maps.isEmpty()) item { Text(t(R.string.offline_no_packages)) }
        items(maps.sortedBy { it.label }, key = { it.id }) { installed ->
            ListItem(
                headlineContent = { Text(installed.label) },
                supportingContent = { Text(t(R.string.offline_received, installed.file.length() / 1048576.0)) },
                trailingContent = {
                    IconButton(onClick = { deleting = installed.id }, enabled = !busy) {
                        Glyph(R.drawable.delete, t(R.string.delete))
                    }
                }
            )
        }
    }

    if (adding) {
        var query by rememberSaveable { mutableStateOf("") }
        val matches = remember(query, cities, catalog) {
            catalog.filter { it.id !in cities && (it.name.contains(query.trim()) ||
                it.region.contains(query.trim()) || it.packageId.contains(query.trim(), ignoreCase = true)) }
        }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(t(R.string.offline_add_city)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(query, { query = it }, t(R.string.offline_search))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 290.dp)) {
                        items(matches, key = { it.id }) { found ->
                            TextButton(onClick = { adding = false; chosen = found.id }) {
                                Text(if (found.name == found.region) found.name else "${found.name} · ${found.region}")
                            }
                        }
                        if (matches.isEmpty()) item { Text(t(R.string.offline_no_matches)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { adding = false }) { Text(t(R.string.cancel)) } }
        )
    }
    if (city != null) {
        val available = maps.any { it.id == city.packageId }
        AlertDialog(
            onDismissRequest = { chosen = null },
            title = { Text(city.name) },
            text = {
                Text(if (available) t(R.string.offline_shared, city.region)
                    else t(R.string.offline_confirm, city.region, city.size))
            },
            confirmButton = {
                TextButton(onClick = { library.add(city); chosen = null }, enabled = !busy) {
                    Text(t(if (available) R.string.offline_add_city else R.string.offline_download))
                }
            },
            dismissButton = { TextButton(onClick = { chosen = null }) { Text(t(R.string.cancel)) } }
        )
    }
    if (map != null) AlertDialog(
        onDismissRequest = { deleting = null },
        title = { Text(map.label) },
        text = { Text(t(R.string.offline_delete_confirm)) },
        confirmButton = { TextButton(onClick = { library.delete(map); deleting = null }) { Text(t(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(t(R.string.cancel)) } }
    )
    error?.let { message ->
        AlertDialog(
            onDismissRequest = library::clearError,
            text = { Text(t(message)) },
            confirmButton = { TextButton(onClick = library::clearError) { Text(t(R.string.ok)) } }
        )
    }
}
