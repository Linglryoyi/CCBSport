package com.ccbsport.app

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

enum class AppLanguage(val tag: String, val label: String) {
    Chinese("zh-CN", "简体中文"),
    English("en", "English"),
    Japanese("ja", "日本語"),
    Korean("ko", "한국어"),
    Russian("ru", "Русский");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    fun context(base: Context): Context = base.createConfigurationContext(
        Configuration().apply { setLocales(LocaleList(this@AppLanguage.locale)) }
    )

    fun text(base: Context, @StringRes id: Int): String = context(base).getString(id)
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.Chinese }
val LocalAppStrings = staticCompositionLocalOf<Context> { error("Missing localized resources") }

@Composable
fun t(@StringRes id: Int, vararg args: Any): String =
    if (args.isEmpty()) LocalAppStrings.current.getString(id) else LocalAppStrings.current.getString(id, *args)

@Composable
fun LanguageScreen(selected: AppLanguage?, onSelect: (AppLanguage) -> Unit) {
    val context = LocalContext.current
    var choice by rememberSaveable { mutableStateOf(selected ?: AppLanguage.entries.firstOrNull {
        it.locale.language == Locale.getDefault().language
    } ?: AppLanguage.Chinese) }
    val resources = remember(context, choice) { choice.context(context) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Bubble(R.drawable.language)
            Spacer(Modifier.height(20.dp))
            Text("CCBSport", style = MaterialTheme.typography.headlineMedium)
            Text(resources.getString(R.string.choose_language), style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            LanguageChoices(choice) { choice = it }
            Spacer(Modifier.height(24.dp))
            Button(onClick = { onSelect(choice) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(resources.getString(R.string.continue_label))
            }
        }
    }
}

@Composable
fun LanguageChoices(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppLanguage.entries.forEach { language ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selected == language) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(language) }.padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(language.label, Modifier.weight(1f))
                    RadioButton(selected = selected == language, onClick = { onSelect(language) })
                }
            }
        }
    }
}
