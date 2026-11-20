package com.playly.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Heading(if (register) R.string.sign_up else R.string.sign_in, R.string.account_subtitle)
        if (register) {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.display_name)) },
                modifier = Modifier.fillMaxWidth(), enabled = !state.profileBusy, singleLine = true)
        }
        OutlinedTextField(email, { email = it.take(254) }, label = { Text(stringResource(R.string.email)) },
            modifier = Modifier.fillMaxWidth(), enabled = !state.profileBusy, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(password, { password = it.take(128) }, label = { Text(stringResource(R.string.password)) },
            modifier = Modifier.fillMaxWidth(), enabled = !state.profileBusy, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        state.profileError?.let { ErrorText(it) }
        if (state.profileBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        PrimaryButton(
            if (register) R.string.sign_up else R.string.sign_in,
            enabled = !state.profileBusy && email.isNotBlank() && password.isNotEmpty() &&
                (!register || (name.isNotBlank() && password.length >= 8)),
        ) { send(Message.Authenticate(email.trim(), password, name.trim().takeIf { register })) }
        TextButton(onClick = { register = !register }, enabled = !state.profileBusy) {
            Text(stringResource(if (register) R.string.have_account else R.string.need_account))
        }
    }
}

@Composable
internal fun ProfileScreen(state: AppState, send: (Message) -> Unit) {
    val profile = state.profile
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Heading(R.string.profile)
        if (state.profileBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (profile == null) {
            Text(stringResource(R.string.guest))
            PrimaryButton(R.string.sign_in, enabled = !state.profileBusy) { send(Message.OpenAuth) }
        } else {
            var name by rememberSaveable(profile.id, profile.displayName) { mutableStateOf(profile.displayName) }
            Text(profile.email, style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.display_name)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.profileBusy)
            PrimaryButton(R.string.save_name, enabled = !state.profileBusy && name.isNotBlank() && name.trim() != profile.displayName) {
                send(Message.UpdateProfile(name.trim()))
            }
            OutlinedButton(onClick = { send(Message.SignOut) }, enabled = !state.profileBusy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sign_out))
            }
        }
        state.profileError?.let {
            ErrorText(it)
            TextButton(onClick = { send(Message.ReloadProfile) }) { Text(stringResource(R.string.retry)) }
        }
        HorizontalDivider()
        OutlinedButton(onClick = { send(Message.OpenAppearance) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.appearance))
        }
    }
}

@Composable
internal fun GameDetailsScreen(details: Details, profile: PlayerProfile?, send: (Message) -> Unit) {
    val game = details.game
    val owner = profile?.id == game.ownerId
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        GameCard(game.title, game.author.displayName, game.id.hashCode(), game.rating.average, game.rating.count)
        if (game.description.isNotBlank()) Text(game.description, style = MaterialTheme.typography.bodyLarge)
        if (game.publication != null) PrimaryButton(R.string.play) { send(Message.ShowPlayerUnavailable) }
        if (game.publication != null && !owner) {
            if (profile == null) {
                OutlinedButton(onClick = { send(Message.OpenAuth) }) { Text(stringResource(R.string.sign_in_to_rate)) }
            } else {
                Text(stringResource(R.string.rate_game), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (1..5).forEach { score ->
                        TextButton(onClick = { send(Message.Rate(score)) }, enabled = !details.busy) { Text(score.toString()) }
                    }
                }
            }
        }
        if (owner) {
            Text(stringResource(R.string.versions), style = MaterialTheme.typography.titleLarge)
            if (!details.busy && details.error == null && details.versions.isEmpty()) {
                Text(stringResource(R.string.no_versions))
            }
            details.versions.forEach { version ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Space.md)) {
                        Text(stringResource(R.string.version_number, version.number), style = MaterialTheme.typography.titleMedium)
                        if (game.publication?.versionId == version.id) Text(stringResource(R.string.published))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            TextButton(onClick = { send(Message.EditVersion(game.id, game.title, version.id)) }, enabled = !details.busy) {
                                Text(stringResource(R.string.edit_game))
                            }
                            if (game.publication?.versionId != version.id) {
                                TextButton(onClick = { send(Message.Publish(version.id)) }, enabled = !details.busy) {
                                    Text(stringResource(R.string.publish))
                                }
                            }
                        }
                    }
                }
            }
            if (details.cursor != null && !details.busy) PrimaryButton(R.string.load_more) { send(Message.NextVersions) }
        }
        if (details.busy) LoadingIndicator()
        details.error?.let {
            ErrorText(it)
            TextButton(onClick = { send(Message.OpenGame(game)) }) { Text(stringResource(R.string.retry)) }
        }
    }
}
