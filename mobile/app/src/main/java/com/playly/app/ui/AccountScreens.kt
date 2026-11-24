package com.playly.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.playly.app.R
import com.playly.app.data.PlayerProfile

@Composable
internal fun AuthScreen(state: AppState, send: (Message) -> Unit) {
    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val actionLabel = if (register) R.string.sign_up else R.string.sign_in
    ScreenColumn {
        StudioHeader(if (register) R.string.registration_title else R.string.sign_in, R.string.account_subtitle)
        Panel {
            if (register) {
                OutlinedTextField(
                    name, { name = it.take(80) }, label = { Text(stringResource(R.string.display_name)) },
                    modifier = Modifier.fillMaxWidth(), enabled = !state.profileBusy, singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
            }
            OutlinedTextField(
                email, { email = it.take(254) }, label = { Text(stringResource(R.string.email)) },
                modifier = Modifier.fillMaxWidth(), enabled = !state.profileBusy, singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            OutlinedTextField(
                password, { password = it.take(128) }, label = { Text(stringResource(R.string.password)) },
                modifier = Modifier.fillMaxWidth(), enabled = !state.profileBusy, singleLine = true,
                shape = MaterialTheme.shapes.medium,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            state.profileError?.let { ErrorText(it) }
            if (state.profileBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            PrimaryButton(
                actionLabel,
                enabled = !state.profileBusy && email.isNotBlank() && password.isNotEmpty() &&
                    (!register || (name.isNotBlank() && password.length >= 8)),
            ) { send(Message.Authenticate(email.trim(), password, name.trim().takeIf { register })) }
        }
        TextButton(
            onClick = { register = !register }, enabled = !state.profileBusy,
            modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = Space.touch),
        ) {
            Text(stringResource(if (register) R.string.have_account else R.string.need_account))
        }
    }
}

@Composable
internal fun ProfileScreen(state: AppState, send: (Message) -> Unit) {
    val profile = state.profile
    ScreenColumn {
        Heading(R.string.profile)
        if (state.profileBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (profile == null) {
            StatePanel(
                R.string.guest, Icons.Outlined.Person, R.string.account_subtitle,
                actionLabel = R.string.sign_in.takeUnless { state.profileBusy },
            ) { send(Message.OpenAuth) }
        } else {
            var name by rememberSaveable(profile.id, profile.displayName) { mutableStateOf(profile.displayName) }
            Panel {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Box(Modifier.size(Space.action), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Person, null)
                        }
                    }
                    Column {
                        Text(profile.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(profile.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    name, { name = it.take(80) }, label = { Text(stringResource(R.string.display_name)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.profileBusy,
                    shape = MaterialTheme.shapes.medium,
                )
                PrimaryButton(
                    R.string.save_name,
                    enabled = !state.profileBusy && name.isNotBlank() && name.trim() != profile.displayName,
                ) { send(Message.UpdateProfile(name.trim())) }
            }
        }
        state.profileError?.let {
            StatePanel(it, error = true, actionLabel = R.string.retry) { send(Message.ReloadProfile) }
        }
        FilledTonalButton(
            onClick = { send(Message.OpenAppearance) },
            modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch),
        ) {
            Icon(Icons.Outlined.Settings, null, Modifier.size(Space.lg))
            Spacer(Modifier.width(Space.sm))
            Text(stringResource(R.string.appearance))
        }
        if (profile != null) {
            OutlinedButton(
                onClick = { send(Message.SignOut) }, enabled = !state.profileBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch),
            ) { Text(stringResource(R.string.sign_out)) }
        }
    }
}

@Composable
internal fun GameDetailsScreen(details: Details, profile: PlayerProfile?, send: (Message) -> Unit) {
    val game = details.game
    val owner = profile?.id == game.ownerId
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        item {
            CoverArt(game.id.hashCode(), Modifier.fillMaxWidth().aspectRatio(1.7f).clip(MaterialTheme.shapes.extraLarge))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(game.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
                Text(game.author.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                game.rating.average?.let { RatingSummary(it, game.rating.count) }
                if (game.description.isNotBlank()) Text(game.description, style = MaterialTheme.typography.bodyLarge)
            }
        }
        game.publication?.let { publication ->
            item {
                PrimaryButton(R.string.play, icon = Icons.Outlined.PlayArrow) {
                    send(Message.Play(PlayRequest(game.id, publication.versionId)))
                }
            }
        }
        if (game.publication != null && !owner) {
            item {
                Panel {
                    SectionTitle(R.string.rate_game)
                    if (profile == null) {
                        OutlinedButton(
                            onClick = { send(Message.OpenAuth) },
                            modifier = Modifier.heightIn(min = Space.touch),
                        ) { Text(stringResource(R.string.sign_in_to_rate)) }
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            (1..5).forEach { score ->
                                val label = stringResource(R.string.rating_score, score)
                                FilledTonalButton(
                                    onClick = { send(Message.Rate(score)) }, enabled = !details.busy,
                                    modifier = Modifier.sizeIn(minWidth = Space.touch, minHeight = Space.touch)
                                        .semantics { contentDescription = label },
                                    contentPadding = PaddingValues(horizontal = Space.sm),
                                ) {
                                    Icon(Icons.Outlined.Star, null, Modifier.size(Space.lg))
                                    Text(score.toString())
                                }
                            }
                        }
                    }
                }
            }
        }
        if (owner) {
            item { SectionTitle(R.string.versions) }
            if (!details.busy && details.error == null && details.versions.isEmpty()) {
                item { StatePanel(R.string.no_versions) }
            }
            items(details.versions, key = { it.id }) { version ->
                Panel {
                    Text(stringResource(R.string.version_number, version.number), style = MaterialTheme.typography.titleLarge)
                    if (game.publication?.versionId == version.id) {
                        Text(stringResource(R.string.published), color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelLarge)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        FilledTonalButton(
                            onClick = { send(Message.Play(PlayRequest(game.id, version.id))) },
                            modifier = Modifier.heightIn(min = Space.touch),
                        ) { Text(stringResource(R.string.play)) }
                        TextButton(
                            onClick = { send(Message.EditVersion(game.id, game.title, version.id)) },
                            enabled = !details.busy,
                            modifier = Modifier.heightIn(min = Space.touch),
                        ) { Text(stringResource(R.string.edit_game)) }
                        if (game.publication?.versionId != version.id) {
                            TextButton(
                                onClick = { send(Message.Publish(version.id)) }, enabled = !details.busy,
                                modifier = Modifier.heightIn(min = Space.touch),
                            ) { Text(stringResource(R.string.publish)) }
                        }
                    }
                }
            }
            if (details.cursor != null && !details.busy) {
                item { PrimaryButton(R.string.load_more) { send(Message.NextVersions) } }
            }
        }
        if (details.busy) item { BusyState() }
        details.error?.let {
            item { StatePanel(it, error = true, actionLabel = R.string.retry) { send(Message.OpenGame(game)) } }
        }
    }
}
