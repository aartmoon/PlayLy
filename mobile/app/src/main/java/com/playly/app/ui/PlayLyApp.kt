package com.playly.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playly.app.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= Space.railBreakpoint
            val showNavigation = state.overlay != Overlay.APPEARANCE &&
                state.overlay != Overlay.AUTH && state.overlay != Overlay.PLAYER
            Scaffold(
                topBar = { AppBar(state.overlay, send) },
                bottomBar = {
                    if (showNavigation && !expanded) AppNavigation(state.tab) { send(Message.Select(it)) }
                },
                containerColor = MaterialTheme.colorScheme.background,
            ) { insets ->
                Row(
                    Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding(),
                ) {
                    if (showNavigation && expanded) {
                        AppNavigationRail(state.tab) { send(Message.Select(it)) }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        val routeWidth = if (state.overlay == Overlay.PLAYER) Modifier
                        else Modifier.widthIn(max = Space.readingWidth)
                        Box(routeWidth.fillMaxSize()) {
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
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryFixed,
                        contentColor = MaterialTheme.colorScheme.onSecondaryFixed,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        PlayLyMark(Modifier.padding(Space.xs), tint = LocalContentColor.current)
                    }
                }
                if (overlay != Overlay.APPEARANCE) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                }
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

private val Tab.presentation
    get() = when (this) {
        Tab.CATALOG -> R.string.catalog to Icons.Outlined.Search
        Tab.CREATE -> R.string.create to Icons.Outlined.Add
        Tab.GAMES -> R.string.my_games to Icons.AutoMirrored.Outlined.List
        Tab.PROFILE -> R.string.profile to Icons.Outlined.Person
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NavigationGlyph(tab: Tab, selected: Boolean) {
    val scale by animateFloatAsState(
        if (selected) 1.12f else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "Selected destination",
    )
    Icon(tab.presentation.second, null, Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
}

@Composable
private fun AppNavigation(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.windowInsetsPadding(NavigationBarDefaults.windowInsets)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            Tab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = selected == tab,
                    onClick = { onSelect(tab) },
                    icon = { NavigationGlyph(tab, selected == tab) },
                    label = { Text(stringResource(tab.presentation.first), maxLines = 2) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryFixed,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.secondaryFixed,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AppNavigationRail(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        Tab.entries.forEach { tab ->
            NavigationRailItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { NavigationGlyph(tab, selected == tab) },
                label = { Text(stringResource(tab.presentation.first)) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryFixed,
                    indicatorColor = MaterialTheme.colorScheme.secondaryFixed,
                ),
            )
        }
    }
}
