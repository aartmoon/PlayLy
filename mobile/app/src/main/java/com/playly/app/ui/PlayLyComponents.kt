package com.playly.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.playly.app.R

@Composable
internal fun ScreenColumn(
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Space.xl),
            content = content,
        )
        if (footer != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.md)) { footer() }
        }
    }
}

internal val ScreenPadding = PaddingValues(
    start = Space.xl, top = Space.md, end = Space.xl, bottom = Space.xl,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Heading(title: Int, subtitle: Int? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.headlineLargeEmphasized,
            modifier = Modifier.semantics { heading() },
        )
        subtitle?.let {
            Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
internal fun SectionTitle(label: Int) {
    Text(stringResource(label), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
}

@Composable
internal fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md), content = content)
    }
}

@Composable
internal fun StatePanel(
    title: Int,
    icon: ImageVector = Icons.Outlined.Info,
    description: Int? = null,
    error: Boolean = false,
    actionLabel: Int? = null,
    action: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = if (error) scheme.errorContainer else scheme.surfaceContainer,
        contentColor = if (error) scheme.onErrorContainer else scheme.onSurface,
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null)
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            }
            description?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            if (actionLabel != null && action != null) {
                FilledTonalButton(onClick = action, modifier = Modifier.heightIn(min = Space.touch)) {
                    Text(stringResource(actionLabel))
                }
            }
        }
    }
}

@Composable
internal fun ErrorText(message: Int) {
    Text(
        stringResource(message),
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BusyState(label: Int = R.string.loading) {
    Row(
        Modifier.fillMaxWidth().padding(Space.lg).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Space.md, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContainedLoadingIndicator(Modifier.size(Space.touch))
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PrimaryButton(
    label: Int,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    action: () -> Unit,
) {
    val accent = playLyAccent
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = action,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = Space.action),
        shapes = ButtonDefaults.shapes(pressedShape = MaterialTheme.shapes.small),
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = accentForeground(accent)),
        border = if (enabled && contrastRatio(accent, scheme.background) < 3f)
            BorderStroke(Space.outline, scheme.primary) else null,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(Space.lg))
            Spacer(Modifier.width(Space.sm))
        }
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun StudioHeader(title: Int, subtitle: Int) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Text(
                stringResource(title),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Text(stringResource(subtitle), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = scheme.secondaryFixed,
                    contentColor = scheme.onSecondaryFixed,
                ) {
                    Box(Modifier.size(Space.touch), contentAlignment = Alignment.Center) {
                        PlayLyMark(tint = LocalContentColor.current)
                    }
                }
            }
        }
    }
}
