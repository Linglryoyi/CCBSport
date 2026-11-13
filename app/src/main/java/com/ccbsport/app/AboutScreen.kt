package com.ccbsport.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

private data class Credit(val name: String, val license: String, val url: String)

private val credits = listOf(
    Credit("AndroidX · Jetpack Compose / Material 3", "Apache License 2.0", "https://github.com/androidx/androidx"),
    Credit("Material Symbols", "Apache License 2.0", "https://github.com/google/material-design-icons"),
    Credit("Kotlin · kotlinx.coroutines", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    Credit("Mapsforge", "LGPL 3.0", "https://github.com/mapsforge/mapsforge/tree/0.25.0"),
    Credit("© OpenStreetMap contributors", "Open Database License 1.0", "https://www.openstreetmap.org/copyright"),
    Credit("Administrative divisions of China", "WTFPL 2.0", "https://github.com/modood/Administrative-divisions-of-China")
)

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(painterResource(R.drawable.ic_app), null, Modifier.size(68.dp))
        Text("CCBSport", style = MaterialTheme.typography.headlineMedium)
        Text(t(R.string.app_version, BuildConfig.VERSION_NAME))
        Text(t(R.string.app_developer, "Linglryoyi"))
        SettingsRow("GitHub", "github.com/Linglryoyi/CCBSport", R.drawable.info, true, true) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Linglryoyi/CCBSport")))
            } catch (_: ActivityNotFoundException) {
                // The repository address remains visible.
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(t(R.string.open_source), style = MaterialTheme.typography.titleLarge)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            credits.forEachIndexed { index, credit ->
                SettingsRow(credit.name, credit.license, R.drawable.info, index == 0, index == credits.lastIndex) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(credit.url)))
                    } catch (_: ActivityNotFoundException) {
                        // License names remain visible without a browser.
                    }
                }
            }
        }
    }
}
