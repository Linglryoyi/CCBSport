package com.ccbsport.app

import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    name: String, avatar: String, language: AppLanguage, changeLanguage: () -> Unit,
    stride: Double, changeStride: (Double) -> Unit, openPage: (SettingsPage) -> Unit,
    theme: AppThemeMode, setTheme: (AppThemeMode) -> Unit,
    save: (String, String) -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    var dialog by rememberSaveable { mutableIntStateOf(0) }
    var strideDraft by rememberSaveable { mutableStateOf("") }
    var strideError by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(name) }
    var draftAvatar by rememberSaveable { mutableStateOf(avatar) }
    var pickerError by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pickerError = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                draftAvatar = uri.toString()
            }.isFailure
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileAvatar(avatar, Modifier.size(104.dp))
        Text(name.ifBlank { t(R.string.athlete) }, style = type.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            SettingsRow(t(R.string.profile), t(R.string.profile_hint), R.drawable.person, true, false) {
                draft = name
                draftAvatar = avatar
                pickerError = false
                dialog = R.string.profile
            }
            SettingsRow(t(R.string.language), language.label, R.drawable.language, false, false, action = changeLanguage)
            SettingsRow(t(R.string.appearance), t(theme.label), R.drawable.dark_mode, false, false) {
                dialog = R.string.appearance
            }
            SettingsRow(t(R.string.offline_maps), t(R.string.offline_settings_hint), R.drawable.map, false, false) {
                openPage(SettingsPage.Offline)
            }
            SettingsRow(t(R.string.stride), t(R.string.stride_value, stride), R.drawable.straighten, false, false) {
                strideDraft = "%.2f".format(java.util.Locale.ROOT, stride)
                strideError = false
                dialog = R.string.stride
            }
            SettingsRow(t(R.string.help), t(R.string.help_hint), R.drawable.help, false, false) { dialog = R.string.help }
            SettingsRow(t(R.string.data_management), t(R.string.data_management_hint), R.drawable.storage, false, false) {
                openPage(SettingsPage.Data)
            }
            SettingsRow(t(R.string.about), t(R.string.app_version, BuildConfig.VERSION_NAME), R.drawable.info, false, true) {
                openPage(SettingsPage.About)
            }
        }
        Text(t(R.string.thanks), Modifier.padding(top = 8.dp), style = type.bodyMedium, color = colors.onSurfaceVariant)
    }
    if (dialog == R.string.profile) {
        AlertDialog(
            onDismissRequest = { dialog = 0 },
            title = { Text(t(dialog)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileAvatar(draftAvatar, Modifier.size(56.dp))
                        OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }) { Text(t(R.string.pick_avatar)) }
                    }
                    if (draftAvatar.isNotBlank()) TextButton(onClick = { draftAvatar = "" }) { Text(t(R.string.remove_avatar)) }
                    Field(draft, { draft = it.take(24) }, t(R.string.nickname))
                    if (pickerError) Text(t(R.string.avatar_error), color = colors.error)
                }
            },
            confirmButton = { TextButton(onClick = { save(draft.trim(), draftAvatar); dialog = 0 }) { Text(t(R.string.save)) } },
            dismissButton = { TextButton(onClick = { dialog = 0 }) { Text(t(R.string.cancel)) } }
        )
    } else if (dialog == R.string.stride) {
        AlertDialog(
            onDismissRequest = { dialog = 0 },
            title = { Text(t(dialog)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t(R.string.stride_help))
                    Field(strideDraft, { strideDraft = it.take(6) }, t(R.string.stride_input), KeyboardType.Decimal)
                    if (strideError) Text(t(R.string.stride_error), color = colors.error)
                }
            },
            confirmButton = { TextButton(onClick = {
                val value = strideDraft.trim().replace(',', '.').toDoubleOrNull()
                if (value == null || value !in .30..2.50) strideError = true
                else {
                    changeStride(value)
                    dialog = 0
                }
            }) { Text(t(R.string.save)) } },
            dismissButton = { TextButton(onClick = { dialog = 0 }) { Text(t(R.string.cancel)) } }
        )
    } else if (dialog == R.string.appearance) {
        AlertDialog(
            onDismissRequest = { dialog = 0 },
            title = { Text(t(R.string.appearance)) },
            text = {
                Column {
                    AppThemeMode.entries.forEach { mode ->
                        TextButton(onClick = { setTheme(mode); dialog = 0 }, modifier = Modifier.fillMaxWidth()) {
                            if (mode == theme) {
                                Glyph(R.drawable.check)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(t(mode.label))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = 0 }) { Text(t(R.string.cancel)) } }
        )
    } else if (dialog == R.string.help) {
        AlertDialog(
            onDismissRequest = { dialog = 0 },
            title = { Text(t(dialog)) },
            text = {
                Text(t(R.string.help_body))
            },
            confirmButton = { TextButton(onClick = { dialog = 0 }) { Text(t(R.string.ok)) } }
        )
    }
}

@Composable
fun ProfileAvatar(avatar: String, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, avatar) {
        value = if (avatar.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(avatar)
                if (Build.VERSION.SDK_INT >= 28) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                        val maxSide = maxOf(info.size.width, info.size.height)
                        if (maxSide > 256) {
                            decoder.setTargetSize(
                                (info.size.width * 256 / maxSide).coerceAtLeast(1),
                                (info.size.height * 256 / maxSide).coerceAtLeast(1)
                            )
                        }
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }.asImageBitmap()
                } else {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    options.inJustDecodeBounds = false
                    options.inSampleSize = (maxOf(options.outWidth, options.outHeight) / 256).coerceAtLeast(1)
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
                }
            }.getOrNull()
        }
    }
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            val image = bitmap
            if (image == null) Glyph(R.drawable.person, t(R.string.avatar), Modifier.fillMaxSize(.52f))
            else Image(image, t(R.string.avatar), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
fun SettingsRow(label: String, hint: String, icon: Int, first: Boolean, last: Boolean,
    enabled: Boolean = true, action: () -> Unit) {
    val top = if (first) 24.dp else 8.dp
    val bottom = if (last) 24.dp else 8.dp
    val colors = MaterialTheme.colorScheme
    Surface(onClick = action, enabled = enabled, shape = RoundedCornerShape(top, top, bottom, bottom), color = colors.surfaceContainerLow) {
        ListItem(
            headlineContent = { Text(label) }, supportingContent = { Text(hint) },
            leadingContent = { Bubble(icon) }, trailingContent = { Glyph(R.drawable.chevron_right) },
            colors = ListItemDefaults.colors(containerColor = colors.surfaceContainerLow)
        )
    }
}
