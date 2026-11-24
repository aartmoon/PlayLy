package com.playly.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playly.app.R

@Composable
fun PlayLyApp(model: PlayLyViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val send = model::dispatch
    val activity = LocalActivity.current
    val dark = state.appearance.isDark()
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    BackHandler(enabled = state.overlay != null || state.tab != Tab.CATALOG) {
        send(if (state.overlay != null) Message.Back else Message.Select(Tab.CATALOG))
    }

    PlayLyTheme(state.appearance) {
        Scaffold(
            topBar = { AppBar(state.overlay, send) },
            bottomBar = {
                if (state.overlay !in listOf(Overlay.APPEARANCE, Overlay.AUTH, Overlay.PLAYER)) {
                    AppNavigation(state.tab) { send(Message.Select(it)) }
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding()) {
                when (state.overlay) {
                    Overlay.APPEARANCE -> AppearanceScreen(state.draftAppearance, send)
                    Overlay.AUTH -> AuthScreen(state, send)
                    Overlay.DETAILS -> state.details?.let { GameDetailsScreen(it, state.profile, send) }
                    Overlay.PLAYER -> state.player?.let { PlayerScreen(it, send) }
                    Overlay.GENERATION -> state.generation?.let { GenerationScreen(it, send) }
                    null -> when (state.tab) {
                        Tab.CATALOG -> CatalogScreen(state, send)
                        Tab.CREATE -> EditorScreen(state.editor, state.generation != null, send)
                        Tab.GAMES -> MyGamesScreen(state, send)
                        Tab.PROFILE -> ProfileScreen(state, send)
                    }
                }
            }
        }
        state.notice?.let { notice ->
            AlertDialog(
                onDismissRequest = { send(Message.DismissNotice) },
                text = { Text(stringResource(notice)) },
                confirmButton = { TextButton(onClick = { send(Message.DismissNotice) }) { Text(stringResource(R.string.close)) } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBar(overlay: Overlay?, send: (Message) -> Unit) {
    val title = when (overlay) {
        Overlay.APPEARANCE -> R.string.appearance
        Overlay.PLAYER -> R.string.play
        Overlay.AUTH -> R.string.account
        Overlay.DETAILS -> R.string.game
        Overlay.GENERATION -> R.string.generation_result
        null -> R.string.app_name
    }
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (overlay == null) {
                    PlayLyMark()
                }
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            }
        },
        navigationIcon = {
            if (overlay != null) {
                IconButton(onClick = { send(Message.Back) }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                }
            }
        },
        actions = {
            if (overlay == null) {
                IconButton(onClick = { send(Message.OpenAppearance) }) {
                    Icon(Icons.Outlined.Settings, stringResource(R.string.appearance))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
internal fun AppNavigation(selected: Tab, onSelect: ((Tab) -> Unit)? = null) {
    val insetModifier = if (onSelect == null) Modifier else Modifier
        .windowInsetsPadding(NavigationBarDefaults.windowInsets)
        .padding(bottom = Space.sm)
    NavigationBar(
        modifier = insetModifier.padding(horizontal = Space.sm).clip(MaterialTheme.shapes.large),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        Tab.entries.forEach { tab ->
            val (label, icon) = when (tab) {
                Tab.CATALOG -> R.string.catalog to Icons.Outlined.Home
                Tab.CREATE -> R.string.create to Icons.Outlined.Add
                Tab.GAMES -> R.string.my_games to Icons.AutoMirrored.Outlined.List
                Tab.PROFILE -> R.string.profile to Icons.Outlined.Person
            }
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect?.invoke(tab) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(stringResource(label), maxLines = 2) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.secondary,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
    }
}

@Composable
internal fun Heading(title: Int, subtitle: Int? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineLarge)
        subtitle?.let {
            Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun ErrorText(message: Int) {
    Text(stringResource(message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
internal fun LoadingIndicator() {
    Box(Modifier.fillMaxWidth().padding(Space.xl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(Space.xl))
    }
}

@Composable
internal fun PrimaryButton(label: Int, enabled: Boolean = true, action: () -> Unit) {
    val accent = playLyAccent
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch),
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = accentForeground(accent)),
        border = if (enabled && contrastRatio(accent, scheme.background) < 3f) BorderStroke(Space.outline, scheme.primary) else null,
    ) {
        Text(stringResource(label))
    }
}
