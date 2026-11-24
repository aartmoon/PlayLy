package com.playly.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.playly.app.R
import java.text.NumberFormat

@Composable
internal fun CatalogScreen(state: AppState, send: (Message) -> Unit) {
    val feed = state.catalog
    val spotlight = feed.games.firstOrNull().takeIf { state.query.isBlank() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        item { Heading(R.string.catalog, R.string.catalog_subtitle) }
        item {
            val searchLabel = stringResource(R.string.search_games)
            TextField(
                value = state.query,
                onValueChange = { send(Message.Search(it.take(200))) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = searchLabel },
                placeholder = { Text(searchLabel) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { send(Message.Search("")) }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.clear_search))
                        }
                    }
                } else null,
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
        if (feed.loading && feed.games.isEmpty()) item { BusyState() }
        feed.error?.let { error ->
            item {
                StatePanel(error, error = true, actionLabel = R.string.retry) { send(Message.LoadFeed(false)) }
            }
        }
        if (!feed.loading && feed.error == null && feed.games.isEmpty()) {
            item {
                val searching = state.query.isNotBlank()
                StatePanel(
                    if (searching) R.string.search_empty else R.string.catalog_empty,
                    icon = Icons.Outlined.Search,
                    actionLabel = R.string.clear_search.takeIf { searching },
                    action = { send(Message.Search("")) },
                )
            }
        }
        spotlight?.let { game ->
            item(key = game.id, contentType = "spotlight") {
                GameCard(game.title, game.author.displayName, game.id.hashCode(), game.rating.average, game.rating.count) {
                    send(Message.OpenGame(game))
                }
            }
        }
        items(if (spotlight == null) feed.games else feed.games.drop(1), key = { it.id }, contentType = { "game" }) {
            GameRow(it.title, it.author.displayName, it.id.hashCode(), it.rating.average, it.rating.count) {
                send(Message.OpenGame(it))
            }
        }
        if (feed.loading && feed.games.isNotEmpty()) item { BusyState() }
        if (feed.cursor != null && !feed.loading && feed.error == null) {
            item { PrimaryButton(R.string.load_more) { send(Message.LoadFeed(false, next = true)) } }
        }
        if (!feed.loading && feed.games.isEmpty() && state.query.isBlank()) {
            item { SectionTitle(R.string.demo_covers) }
            item { GameCard(stringResource(R.string.demo_rooftops), null, 0) }
            item { GameRow(stringResource(R.string.demo_moon), null, 1) }
            item { GameRow(stringResource(R.string.demo_forest), null, 2) }
        }
    }
}

@Composable
internal fun MyGamesScreen(state: AppState, send: (Message) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        item { Heading(R.string.my_games, R.string.my_games_subtitle) }
        state.generation?.let {
            item {
                FilledTonalButton(onClick = { send(Message.OpenGeneration) }, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) {
                    Text(stringResource(R.string.generation_result))
                }
            }
        }
        if (state.profile == null) {
            item {
                StatePanel(R.string.guest, Icons.Outlined.Person, R.string.account_subtitle, actionLabel = R.string.sign_in) {
                    send(Message.OpenAuth)
                }
            }
        } else {
            item { PrimaryButton(R.string.create_game, icon = Icons.Outlined.Add) { send(Message.Select(Tab.CREATE)) } }
            val feed = state.myGames
            if (feed.loading && feed.games.isEmpty()) item { BusyState() }
            feed.error?.let { error ->
                item { StatePanel(error, error = true, actionLabel = R.string.retry) { send(Message.LoadFeed(true)) } }
            }
            if (!feed.loading && feed.error == null && feed.games.isEmpty()) {
                item { StatePanel(R.string.my_games_empty, Icons.Outlined.Add) }
            }
            items(feed.games, key = { it.id }) { game ->
                val status = if (game.publication == null) R.string.draft else R.string.published
                GameRow(game.title, stringResource(status), game.id.hashCode(), game.rating.average, game.rating.count) {
                    send(Message.OpenGame(game))
                }
            }
            if (feed.loading && feed.games.isNotEmpty()) item { BusyState() }
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
    val shape = MaterialTheme.shapes.extraLarge
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val content: @Composable ColumnScope.() -> Unit = {
        CoverArt(cover, Modifier.fillMaxWidth().aspectRatio(1.85f))
        Row(
            Modifier.fillMaxWidth().padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            GameCaption(title, byline, rating, ratingCount, Modifier.weight(1f), prominent = true)
            if (onClick != null) GameArrow()
        }
    }
    if (onClick == null) Card(Modifier.fillMaxWidth(), shape = shape, colors = colors, content = content)
    else Card(onClick, Modifier.fillMaxWidth(), shape = shape, colors = colors, content = content)
}

@Composable
private fun GameRow(
    title: String,
    byline: String?,
    cover: Int,
    rating: Double? = null,
    ratingCount: Long = 0,
    onClick: (() -> Unit)? = null,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val content: @Composable ColumnScope.() -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            CoverArt(cover, Modifier.size(width = Space.thumbnailWidth, height = Space.action).clip(MaterialTheme.shapes.small))
            GameCaption(title, byline, rating, ratingCount, Modifier.weight(1f))
            if (onClick != null) Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(end = Space.sm))
        }
    }
    if (onClick == null) Card(Modifier.fillMaxWidth(), colors = colors, content = content)
    else Card(onClick, Modifier.fillMaxWidth(), colors = colors, content = content)
}

@Composable
private fun GameArrow() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryFixed,
        contentColor = MaterialTheme.colorScheme.onSecondaryFixed,
    ) {
        Box(Modifier.size(Space.touch), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
        }
    }
}

@Composable
private fun GameCaption(
    title: String,
    byline: String?,
    rating: Double?,
    ratingCount: Long,
    modifier: Modifier,
    prominent: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(
            title,
            style = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        byline?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (rating != null) RatingSummary(rating, ratingCount)
    }
}

@Composable
internal fun RatingSummary(rating: Double, count: Long) {
    val number = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        Icon(Icons.Outlined.Star, null, Modifier.size(Space.lg), tint = MaterialTheme.colorScheme.secondary)
        Text(stringResource(R.string.rating_value, number.format(rating), number.format(count)),
            style = MaterialTheme.typography.labelMedium)
    }
}
