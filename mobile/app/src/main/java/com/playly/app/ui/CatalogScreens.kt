package com.playly.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.playly.app.R
import java.text.NumberFormat

@Composable
internal fun CatalogScreen(state: AppState, send: (Message) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.lg, end = Space.lg, bottom = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item { Heading(R.string.catalog, R.string.catalog_subtitle) }
        item {
            OutlinedTextField(
                state.query, { send(Message.Search(it.take(200))) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_games)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
        }
        val feed = state.catalog
        if (feed.loading && feed.games.isEmpty()) item { LoadingIndicator() }
        feed.error?.let { error ->
            item {
                ErrorText(error)
                TextButton(onClick = { send(Message.LoadFeed(false)) }) { Text(stringResource(R.string.retry)) }
            }
        }
        if (!feed.loading && feed.error == null && feed.games.isEmpty()) {
            item { Text(stringResource(if (state.query.isBlank()) R.string.catalog_empty else R.string.search_empty)) }
        }
        items(feed.games, key = { it.id }) { game ->
            GameCard(game.title, game.author.displayName, game.id.hashCode(), game.rating.average, game.rating.count) {
                send(Message.OpenGame(game))
            }
        }
        if (feed.cursor != null && !feed.loading && feed.error == null) {
            item { PrimaryButton(R.string.load_more) { send(Message.LoadFeed(false, next = true)) } }
        }
        if (!feed.loading && feed.games.isEmpty() && state.query.isBlank()) {
            item { Text(stringResource(R.string.demo_covers), style = MaterialTheme.typography.titleSmall) }
            item { GameCard(stringResource(R.string.demo_rooftops), null, 0) }
            item { GameCard(stringResource(R.string.demo_moon), null, 1) }
            item { GameCard(stringResource(R.string.demo_forest), null, 2) }
        }
    }
}

@Composable
internal fun MyGamesScreen(state: AppState, send: (Message) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.lg, end = Space.lg, bottom = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item { Heading(R.string.my_games, R.string.my_games_subtitle) }
        state.generation?.let {
            item { OutlinedButton(onClick = { send(Message.OpenGeneration) }) { Text(stringResource(R.string.generation_result)) } }
        }
        if (state.profile == null) {
            item { PrimaryButton(R.string.sign_in) { send(Message.OpenAuth) } }
        } else {
            item { PrimaryButton(R.string.create_game) { send(Message.Select(Tab.CREATE)) } }
            val feed = state.myGames
            if (feed.loading) item { LoadingIndicator() }
            feed.error?.let { message -> item {
                ErrorText(message)
                TextButton(onClick = { send(Message.LoadFeed(true)) }) { Text(stringResource(R.string.retry)) }
            } }
            if (!feed.loading && feed.error == null && feed.games.isEmpty()) {
                item { Text(stringResource(R.string.my_games_empty)) }
            }
            items(feed.games, key = { it.id }) { game ->
                val status = if (game.publication == null) R.string.draft else R.string.published
                GameCard(game.title, stringResource(status), game.id.hashCode(), game.rating.average, game.rating.count) {
                    send(Message.OpenGame(game))
                }
            }
            if (feed.cursor != null && !feed.loading && feed.error == null) {
                item { PrimaryButton(R.string.load_more) { send(Message.LoadFeed(true, next = true)) } }
            }
        }
    }
}

@Composable
internal fun GameCard(
    title: String,
    byline: String?,
    cover: Int,
    rating: Double? = null,
    ratingCount: Long = 0,
    onClick: (() -> Unit)? = null,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val body: @Composable ColumnScope.() -> Unit = {
        CoverArt(cover, Modifier.fillMaxWidth().aspectRatio(2.15f))
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                if (byline != null) {
                    Text(byline, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (rating != null) {
                    Icon(Icons.Outlined.Star, contentDescription = null, Modifier.size(Space.lg), tint = MaterialTheme.colorScheme.secondary)
                    val number = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
                    Text(stringResource(R.string.rating_value, number.format(rating), number.format(ratingCount)), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (onClick == null) Card(Modifier.fillMaxWidth(), colors = colors, content = body)
    else Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = colors, content = body)
}
