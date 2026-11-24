package com.playly.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.playly.app.R
import com.playly.app.data.GenerationStatus
import java.util.UUID

@Composable
internal fun EditorScreen(editor: Editor, hasGeneration: Boolean, send: (Message) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            Heading(R.string.create_heading, R.string.create_subtitle)
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(stringResource(R.string.project_name), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    editor.title, { send(Message.ChangeTitle(it)) },
                    modifier = Modifier.fillMaxWidth(), enabled = !editor.sending && editor.gameId == null,
                    singleLine = true, placeholder = { Text(stringResource(R.string.project_placeholder)) },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(stringResource(R.string.describe_game), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    editor.prompt, { send(Message.ChangePrompt(it)) },
                    modifier = Modifier.fillMaxWidth(), enabled = !editor.sending, minLines = 5, maxLines = 9,
                    placeholder = { Text(stringResource(R.string.prompt_placeholder)) },
                    supportingText = {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text(stringResource(R.string.prompt_count, editor.prompt.length))
                        }
                    },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(stringResource(R.string.prompt_examples), style = MaterialTheme.typography.titleSmall)
                listOf(R.string.prompt_space, R.string.prompt_city).forEach { example ->
                    val text = stringResource(example)
                    OutlinedButton(
                        onClick = { send(Message.ChangePrompt(text)) },
                        enabled = !editor.sending,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(Space.outline, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Icon(Icons.Outlined.Star, null, Modifier.size(Space.lg), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(Space.sm))
                        Text(text, modifier = Modifier.weight(1f))
                    }
                }
            }
            editor.error?.let { ErrorText(it) }
            if (hasGeneration) TextButton(onClick = { send(Message.OpenGeneration) }) {
                Text(stringResource(R.string.generation_result))
            }
        }
        if (editor.sending) LinearProgressIndicator(Modifier.fillMaxWidth())
        PrimaryButton(
            if (editor.baseVersion == null) R.string.create_game else R.string.edit_game,
            enabled = !editor.sending && editor.title.isNotBlank() && editor.prompt.isNotBlank(),
        ) { send(Message.Submit(UUID.randomUUID())) }
        Spacer(Modifier.height(Space.sm))
    }
}

@Composable
internal fun GenerationScreen(generation: GenerationUi, send: (Message) -> Unit) {
    val ready = generation.status == GenerationStatus.Status.SUCCEEDED
    val failed = generation.status == GenerationStatus.Status.FAILED
    val title = when (generation.status) {
        GenerationStatus.Status.QUEUED -> R.string.generation_queued
        GenerationStatus.Status.RUNNING -> R.string.generation_running
        GenerationStatus.Status.SUCCEEDED -> R.string.generation_ready
        GenerationStatus.Status.FAILED -> R.string.generation_failed
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = Space.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            if (ready) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Outlined.Check, null, Modifier.padding(Space.md).size(Space.xxl),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            } else if (generation.polling) {
                CircularProgressIndicator(Modifier.size(Space.xxl))
            }
            Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
            Text(
                generation.versionNumber?.let { stringResource(R.string.generation_version, it, generation.ticket.title) }
                    ?: generation.ticket.title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ready && generation.versionId != null) {
            val version = generation.versionId
            val play = { send(Message.Play(PlayRequest(generation.ticket.gameId, version))) }
            Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                CoverArt(generation.ticket.gameId.hashCode(), Modifier.matchParentSize())
                FilledTonalIconButton(onClick = play, modifier = Modifier.size(Space.touch)) {
                    Icon(Icons.Outlined.PlayArrow, stringResource(R.string.play))
                }
            }
            PrimaryButton(R.string.play, action = play)
            OutlinedButton(
                onClick = { send(Message.EditVersion(generation.ticket.gameId, generation.ticket.title, version)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch),
            ) { Text(stringResource(R.string.edit_game)) }
        } else if (failed) {
            ErrorText(generation.error ?: R.string.generation_failed_detail)
            PrimaryButton(R.string.return_to_editor) { send(Message.EditFailedGeneration) }
        } else {
            Text(stringResource(R.string.generation_pending), color = MaterialTheme.colorScheme.onSurfaceVariant)
            generation.error?.let { ErrorText(it) }
            if (!generation.polling) PrimaryButton(R.string.retry_status) { send(Message.RetryPolling) }
        }
        TextButton(onClick = { send(Message.Select(Tab.GAMES)) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.go_to_games), color = MaterialTheme.colorScheme.secondary)
        }
    }
}
