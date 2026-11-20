package com.playly.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
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
    var backgroundInput by rememberSaveable { mutableStateOf(value.background.asHex()) }
    var accentInput by rememberSaveable { mutableStateOf(value.accent.asHex()) }
    val background = backgroundInput.parseHexColor()
    val accent = accentInput.parseHexColor()
    val preview = value.copy(background = background ?: value.background, accent = accent ?: value.accent)
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
                        val sample = preview.copy(mode = mode)
                        PlayLyTheme(sample) {
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(Space.outline, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Box(Modifier.size(Space.touch), contentAlignment = Alignment.Center) { PlayLyMark() }
                            }
                        }
                        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                        RadioButton(selected, onClick = null)
                    }
                }
            }
        }
        if (value.mode == ThemeMode.CUSTOM) {
            ColorSetting(
                title = R.string.theme_background,
                hexLabel = R.string.theme_background_hex,
                colors = backgrounds,
                input = backgroundInput,
                onInput = { backgroundInput = it },
            )
            ColorSetting(
                title = R.string.theme_accent,
                hexLabel = R.string.theme_accent_hex,
                colors = accents,
                input = accentInput,
                onInput = { accentInput = it },
            )
        }
        Text(stringResource(R.string.theme_preview), style = MaterialTheme.typography.titleMedium)
        PlayLyTheme(preview) {
            OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.background)) {
                Column(Modifier.padding(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Row(
                        modifier = Modifier.padding(horizontal = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        PlayLyMark()
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    }
                    GameCard(stringResource(R.string.demo_rooftops), stringResource(R.string.demo_covers), 0)
                    Surface(
                        color = playLyAccent, contentColor = accentForeground(playLyAccent),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Box(Modifier.fillMaxWidth().heightIn(min = Space.touch), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.play), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        PrimaryButton(R.string.theme_save, enabled = value.mode != ThemeMode.CUSTOM || (background != null && accent != null)) {
            send(Message.ChangeAppearance(preview))
            send(Message.SaveAppearance)
        }
        OutlinedButton(
            onClick = {
                val defaults = Appearance()
                backgroundInput = defaults.background.asHex()
                accentInput = defaults.accent.asHex()
                send(Message.ChangeAppearance(defaults))
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch),
        ) { Text(stringResource(R.string.theme_reset)) }
    }
}

@Composable
private fun ColorSetting(title: Int, hexLabel: Int, colors: List<Int>, input: String, onInput: (String) -> Unit) {
    val selected = input.parseHexColor()
    val titleText = stringResource(title)
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(titleText, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            colors.forEach { color ->
                val label = stringResource(R.string.theme_swatch, titleText, color.asHex())
                IconButton(
                    onClick = { onInput(color.asHex()) },
                    modifier = Modifier.size(Space.touch).semantics { contentDescription = label },
                ) {
                    val swatch = Color(color)
                    Box(
                        Modifier.size(Space.xxl).background(swatch, CircleShape)
                            .border(Space.outline, MaterialTheme.colorScheme.outline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected == color) {
                            Icon(Icons.Outlined.Check, null, Modifier.size(Space.lg), tint = accentForeground(swatch))
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            input, { onInput(it.take(7)) }, Modifier.fillMaxWidth(),
            singleLine = true, label = { Text(stringResource(hexLabel)) }, isError = selected == null,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            supportingText = if (selected == null) ({ Text(stringResource(R.string.theme_hex_error)) }) else null,
        )
    }
}

internal fun String.parseHexColor(): Int? {
    val digits = removePrefix("#")
    if (digits.length != 6 || digits.any { it !in "0123456789ABCDEFabcdef" }) return null
    return digits.toInt(16) or 0xFF000000.toInt()
}

private fun Int.asHex(): String = String.format(Locale.ROOT, "#%06X", this and 0xFFFFFF)
