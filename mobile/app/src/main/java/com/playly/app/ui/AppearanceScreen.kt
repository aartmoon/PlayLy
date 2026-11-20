package com.playly.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import com.playly.app.R
import com.playly.app.data.Appearance
import com.playly.app.data.ThemeMode
import java.util.Locale

@Composable
internal fun AppearanceScreen(value: Appearance, send: (Message) -> Unit) {
    var hexInput by rememberSaveable(value.accent) { mutableStateOf(value.accent.asHex()) }
    val parsed = hexInput.removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Text(stringResource(R.string.appearance_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            ThemeMode.entries.forEach { mode ->
                val selected = value.mode == mode
                val label = when (mode) {
                    ThemeMode.LIGHT -> R.string.theme_light
                    ThemeMode.DARK -> R.string.theme_dark
                    ThemeMode.CUSTOM -> R.string.theme_custom
                }
                OutlinedCard(
                    modifier = Modifier.weight(1f),
                    onClick = { send(Message.ChangeAppearance(value.copy(mode = mode))) },
                    border = BorderStroke(Space.outline, if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.fillMaxWidth().padding(top = Space.lg, bottom = Space.md),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        val dark = mode != ThemeMode.LIGHT
                        Surface(color = if (dark) Color(0xFF32342F) else Color(0xFFF0F0E8), shape = MaterialTheme.shapes.small) {
                            Box(Modifier.size(Space.touch), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Settings, null, tint = if (dark) Color.White else Color.DarkGray)
                            }
                        }
                        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                        RadioButton(selected, onClick = null)
                    }
                }
            }
        }
        Text(stringResource(R.string.theme_accent), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            accents.forEach { color ->
                val label = stringResource(R.string.accent_accessibility, color.asHex())
                IconButton(
                    onClick = { send(Message.ChangeAppearance(value.copy(mode = ThemeMode.CUSTOM, accent = color))) },
                    modifier = Modifier.size(Space.touch).semantics { contentDescription = label },
                ) {
                    Box(Modifier.size(Space.xxl).background(Color(color), CircleShape), contentAlignment = Alignment.Center) {
                        if (value.accent == color) Icon(Icons.Outlined.Check, null, Modifier.size(Space.lg), tint = accentForeground(Color(color)))
                    }
                }
            }
        }
        if (value.mode == ThemeMode.CUSTOM) {
            OutlinedTextField(hexInput, { hexInput = it.take(7) }, Modifier.fillMaxWidth(),
                singleLine = true, label = { Text(stringResource(R.string.theme_hex)) }, isError = parsed == null,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                supportingText = if (parsed == null) ({ Text(stringResource(R.string.theme_hex_error)) }) else null,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.theme_dark_base), Modifier.weight(1f))
                Switch(checked = value.customDark, onCheckedChange = { send(Message.ChangeAppearance(value.copy(customDark = it))) })
            }
        }
        val preview = if (value.mode == ThemeMode.CUSTOM && parsed != null) value.copy(accent = parsed) else value
        Text(stringResource(R.string.theme_preview), style = MaterialTheme.typography.titleMedium)
        PlayLyTheme(preview) {
            OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.background)) {
                Column(Modifier.padding(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    GameCard(stringResource(R.string.demo_rooftops), stringResource(R.string.demo_covers), 0)
                    Surface(
                        color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Box(Modifier.fillMaxWidth().heightIn(min = Space.touch), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.play), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        PrimaryButton(R.string.theme_save, enabled = value.mode != ThemeMode.CUSTOM || parsed != null) {
            send(Message.ChangeAppearance(preview))
            send(Message.SaveAppearance)
        }
        OutlinedButton(
            onClick = { hexInput = Appearance().accent.asHex(); send(Message.ChangeAppearance(Appearance())) },
            modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch),
        ) { Text(stringResource(R.string.theme_reset)) }
    }
}

private fun Int.asHex(): String = String.format(Locale.ROOT, "#%06X", this and 0xFFFFFF)
