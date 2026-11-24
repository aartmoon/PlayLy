package com.playly.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.playly.app.R
import com.playly.app.data.GenerationStatus
import java.util.UUID

@Composable
internal fun EditorScreen(editor: Editor, hasGeneration: Boolean, send: (Message) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            StudioHeader(
                title = if (editor.baseVersion == null) R.string.create_heading else R.string.edit_game,
                subtitle = if (editor.baseVersion == null) R.string.create_subtitle else R.string.edit_subtitle,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedTextField(
                    editor.title, { send(Message.ChangeTitle(it)) },
                    modifier = Modifier.fillMaxWidth(), enabled = !editor.sending && editor.gameId == null,
                    singleLine = true, label = { Text(stringResource(R.string.project_name)) },
                    placeholder = { Text(stringResource(R.string.project_placeholder)) },
                    shape = MaterialTheme.shapes.medium,
                )
            }
            Panel {
                SectionTitle(R.string.describe_game)
                OutlinedTextField(
                    editor.prompt, { send(Message.ChangePrompt(it)) },
                    modifier = Modifier.fillMaxWidth(), enabled = !editor.sending, minLines = 4, maxLines = 9,
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(stringResource(R.string.prompt_label)) },
                    placeholder = { Text(stringResource(R.string.prompt_placeholder)) },
                    supportingText = {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text(stringResource(R.string.prompt_count, editor.prompt.length))
                        }
                    },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                SectionTitle(R.string.prompt_examples)
                listOf(R.string.prompt_space, R.string.prompt_city).forEach { example ->
                    val text = stringResource(example)
                    OutlinedButton(
                        onClick = { send(Message.ChangePrompt(text)) },
                        enabled = !editor.sending,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch),
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
            if (hasGeneration) TextButton(onClick = { send(Message.OpenGeneration) }, modifier = Modifier.heightIn(min = Space.touch)) {
                Text(stringResource(R.string.generation_result))
            }
        }
        if (editor.sending) LinearProgressIndicator(Modifier.fillMaxWidth())
        PrimaryButton(
            if (editor.baseVersion == null) R.string.create_game else R.string.edit_game,
            enabled = !editor.sending && editor.title.isNotBlank() && editor.prompt.isNotBlank(),
            icon = if (editor.baseVersion == null) Icons.Outlined.Add else null,
        ) { send(Message.Submit(UUID.randomUUID())) }
        Spacer(Modifier.height(Space.sm))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GenerationScreen(generation: GenerationUi, send: (Message) -> Unit) {
    val ready = generation.status == GenerationStatus.Status.SUCCEEDED
    val failed = generation.status == GenerationStatus.Status.FAILED
    ScreenColumn {
        val effects = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = generation.status,
            transitionSpec = {
                fadeIn(effects.defaultEffectsSpec()) togetherWith fadeOut(effects.fastEffectsSpec())
            },
            label = "Generation status",
        ) { status ->
            val title = when (status) {
                GenerationStatus.Status.QUEUED -> R.string.generation_queued
                GenerationStatus.Status.RUNNING -> R.string.generation_running
                GenerationStatus.Status.SUCCEEDED -> R.string.generation_ready
                GenerationStatus.Status.FAILED -> R.string.generation_failed
            }
            val scheme = MaterialTheme.colorScheme
            val succeeded = status == GenerationStatus.Status.SUCCEEDED
            val failure = status == GenerationStatus.Status.FAILED
            Surface(
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                shape = MaterialTheme.shapes.extraLarge,
                color = when {
                    failure -> scheme.errorContainer
                    succeeded -> scheme.secondaryFixed
                    else -> scheme.primaryContainer
                },
                contentColor = when {
                    failure -> scheme.onErrorContainer
                    succeeded -> scheme.onSecondaryFixed
                    else -> scheme.onPrimaryContainer
                },
            ) {
                Column(
                    Modifier.padding(Space.xl),
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    when {
                        succeeded -> Icon(Icons.Outlined.Check, null, Modifier.size(Space.xxl))
                        failure -> Icon(Icons.Outlined.Info, null, Modifier.size(Space.xxl))
                        generation.polling -> LoadingIndicator(Modifier.size(Space.xxl), color = LocalContentColor.current)
                        else -> PlayLyMark(tint = LocalContentColor.current)
                    }
                    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        generation.versionNumber?.takeIf { succeeded }?.let {
                            stringResource(R.string.generation_version, it, generation.ticket.title)
                        } ?: generation.ticket.title,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
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
            PrimaryButton(R.string.play, icon = Icons.Outlined.PlayArrow, action = play)
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
        TextButton(onClick = { send(Message.Select(Tab.GAMES)) }, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = Space.touch)) {
            Text(stringResource(R.string.go_to_games), color = MaterialTheme.colorScheme.secondary)
        }
    }
}
