package com.playly.app.ui

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playly.app.R
import com.playly.app.data.CoreError
import com.playly.app.data.CoreNotConfigured
import com.playly.app.data.CoreRepository
import com.playly.app.data.GameCardData
import com.playly.app.data.GenerationStatus
import com.playly.app.data.GenerationTicket
import com.playly.app.data.LocalSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

class PlayLyViewModel @Inject constructor(
    private val repository: CoreRepository,
    private val settings: LocalSettings,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppState(appearance = settings.readAppearance()))
    val state = mutableState.asStateFlow()

    private enum class Work { CATALOG, MY_GAMES, PROFILE, SUBMIT, POLL, DETAILS }
    private val jobs = mutableMapOf<Work, Job>()

    init {
        dispatch(Message.Start)
    }

    @MainThread
    fun dispatch(message: Message) {
        val transition = update(mutableState.value, message)
        mutableState.value = transition.state
        transition.commands.forEach(::execute)
    }

    private fun execute(command: Command) {
        when (command) {
            is Command.StoreAppearance -> settings.writeAppearance(command.value)
            is Command.FetchFeed -> runWork(
                if (command.mine) Work.MY_GAMES else Work.CATALOG,
                { Message.FeedFailed(command.mine, it) },
            ) {
                if (command.debounce) delay(350)
                val page = network {
                    if (command.mine) repository.myGames(command.cursor)
                    else repository.catalog(command.query, command.cursor)
                }
                dispatch(Message.FeedLoaded(command.mine, command.cursor != null, page))
            }
            Command.FetchProfile -> runWork(Work.PROFILE, Message::ProfileFailed) {
                val profile = network { repository.profile() }
                dispatch(Message.ProfileLoaded(profile, profile?.let { settings.readTicket(it.id) }))
            }
            is Command.SignIn -> runWork(Work.PROFILE, Message::ProfileFailed) {
                val profile = network { with(command.message) { repository.signIn(email, password, name) } }
                dispatch(Message.ProfileLoaded(profile, settings.readTicket(profile.id)))
            }
            is Command.SaveProfile -> runWork(Work.PROFILE, Message::ProfileFailed) {
                dispatch(Message.ProfileLoaded(network { repository.updateProfile(command.name) }))
            }
            Command.LogOut -> {
                listOf(Work.MY_GAMES, Work.POLL, Work.SUBMIT, Work.DETAILS).forEach { jobs[it]?.cancel() }
                runWork(Work.PROFILE, Message::ProfileFailed) {
                    network { repository.signOut() }
                    dispatch(Message.ProfileLoaded(null))
                }
            }
            is Command.SendPrompt -> runWork(Work.SUBMIT, Message::SubmitFailed) {
                val editor = command.editor
                val gameId = editor.gameId ?: network { repository.createDraft(editor.title.trim(), "") }.id
                    .also { dispatch(Message.DraftCreated(it)) }
                val generation = network {
                    repository.submit(gameId, requireNotNull(editor.key), editor.prompt, editor.baseVersion)
                }
                val ticket = GenerationTicket(generation.id, gameId, editor.title, command.ownerId, editor.prompt, editor.baseVersion)
                settings.writeTicket(ticket)
                dispatch(Message.Accepted(ticket, generation))
            }
            is Command.Poll -> runWork(Work.POLL, { Message.PollFailed(command.ticket.id, it) }) {
                while (true) {
                    val status = network { repository.status(command.ticket.id) }
                    val terminal = status.status == GenerationStatus.Status.SUCCEEDED || status.status == GenerationStatus.Status.FAILED
                    val version = status.resultVersionId?.let { id ->
                        try {
                            network { repository.version(status.gameId, id).number }
                        } catch (_: IOException) {
                            null
                        }
                    }
                    dispatch(Message.Progress(status, version))
                    if (terminal) break
                    delay(3_000)
                }
            }
            is Command.FetchDetails -> runDetails(command.game) {
                fetchDetails(command.game, command.viewerId, command.previous)
            }
            is Command.Publish -> runDetails(command.game) {
                network { repository.publish(command.game.id, command.version) }
                fetchDetails(command.game, command.viewerId)
                dispatch(Message.LoadFeed(false))
            }
            is Command.Rate -> runDetails(command.game) {
                network { repository.rate(command.game.id, command.score) }
                fetchDetails(command.game, command.viewerId)
                dispatch(Message.LoadFeed(false))
            }
        }
    }

    private suspend fun fetchDetails(game: GameCardData, viewerId: UUID?, previous: Details? = null) {
        val refreshed = network { repository.game(game.id, viewerId != null) }
        val versions = if (viewerId == refreshed.ownerId) network { repository.versions(game.id, previous?.cursor) } else null
        dispatch(Message.DetailsLoaded(Details(refreshed,
            (previous?.versions.orEmpty() + versions?.items.orEmpty()).distinctBy { it.id },
            versions?.nextCursor, busy = false,
        )))
    }

    private fun runDetails(game: GameCardData, block: suspend () -> Unit) {
        runWork(Work.DETAILS, { Message.DetailsFailed(game.id, it) }, block)
    }

    private fun runWork(work: Work, onFailure: (Int) -> Message, block: suspend () -> Unit) {
        jobs[work]?.cancel()
        jobs[work] = viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                dispatch(onFailure(error.toMessage()))
            }
        }
    }

    private suspend fun <T> network(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun Exception.toMessage(): Int = when (this) {
        is CoreNotConfigured -> R.string.core_not_configured
        is CoreError -> when (status) {
            400 -> R.string.invalid_input
            401 -> R.string.auth_required_or_invalid
            404 -> R.string.not_found
            409 -> if (code == "GENERATION_IN_PROGRESS") R.string.generation_already_running else R.string.conflict
            429 -> R.string.rate_limit
            else -> R.string.server_error
        }
        is IOException -> R.string.connection_error
        else -> R.string.unexpected_error
    }
}
