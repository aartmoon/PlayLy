package com.playly.app.ui

import com.playly.app.R
import com.playly.app.data.Appearance
import com.playly.app.data.GenerationStatus
import com.playly.app.data.GenerationTicket
import com.playly.app.data.GameCardData
import com.playly.app.data.LocalGame
import com.playly.app.data.Page
import com.playly.app.data.PlayerProfile
import com.playly.app.data.ProjectVersion
import java.util.UUID

enum class Tab { CATALOG, CREATE, GAMES, PROFILE }
enum class Overlay { APPEARANCE, DETAILS, GENERATION, AUTH, PLAYER }

data class Feed(
    val games: List<GameCardData> = emptyList(),
    val cursor: String? = null,
    val loading: Boolean = false,
    val error: Int? = null,
)

data class Editor(
    val title: String = "",
    val prompt: String = "",
    val gameId: UUID? = null,
    val baseVersion: UUID? = null,
    val key: UUID? = null,
    val sending: Boolean = false,
    val error: Int? = null,
)

data class GenerationUi(
    val ticket: GenerationTicket,
    val status: GenerationStatus.Status = GenerationStatus.Status.QUEUED,
    val versionId: UUID? = null,
    val versionNumber: Int? = null,
    val error: Int? = null,
    val polling: Boolean = true,
)

data class Details(
    val game: GameCardData,
    val versions: List<ProjectVersion> = emptyList(),
    val cursor: String? = null,
    val busy: Boolean = true,
    val error: Int? = null,
)

data class PlayRequest(val game: UUID, val version: UUID)
data class PlayerUi(val request: PlayRequest, val from: Overlay?, val packageFiles: LocalGame? = null, val error: Int? = null)

data class AppState(
    val tab: Tab = Tab.CATALOG,
    val overlay: Overlay? = null,
    val authReturn: Overlay? = null,
    val appearance: Appearance = Appearance(),
    val draftAppearance: Appearance = appearance,
    val query: String = "",
    val catalog: Feed = Feed(),
    val myGames: Feed = Feed(),
    val profile: PlayerProfile? = null,
    val profileBusy: Boolean = true,
    val profileError: Int? = null,
    val editor: Editor = Editor(),
    val generation: GenerationUi? = null,
    val details: Details? = null,
    val player: PlayerUi? = null,
    val notice: Int? = null,
)

sealed interface Message {
    data object Start : Message
    data class Select(val tab: Tab) : Message
    data object Back : Message
    data class Search(val query: String) : Message
    data class LoadFeed(val mine: Boolean, val next: Boolean = false) : Message
    data class FeedLoaded(val mine: Boolean, val append: Boolean, val page: Page<GameCardData>) : Message
    data class FeedFailed(val mine: Boolean, val error: Int) : Message
    data object OpenAppearance : Message
    data class ChangeAppearance(val value: Appearance) : Message
    data object SaveAppearance : Message
    data object OpenAuth : Message
    data class Authenticate(val email: String, val password: String, val name: String?) : Message
    data object ReloadProfile : Message
    data class UpdateProfile(val name: String) : Message
    data object SignOut : Message
    data class ProfileLoaded(val profile: PlayerProfile?, val ticket: GenerationTicket? = null) : Message
    data class ProfileFailed(val error: Int) : Message
    data class ChangeTitle(val value: String) : Message
    data class ChangePrompt(val value: String) : Message
    data class Submit(val key: UUID) : Message
    data class DraftCreated(val gameId: UUID) : Message
    data class Accepted(val ticket: GenerationTicket, val status: GenerationStatus) : Message
    data class SubmitFailed(val error: Int) : Message
    data object OpenGeneration : Message
    data object RetryPolling : Message
    data class Progress(val status: GenerationStatus, val version: Int? = null) : Message
    data class PollFailed(val id: UUID, val error: Int) : Message
    data class OpenGame(val game: GameCardData) : Message
    data class DetailsLoaded(val value: Details) : Message
    data class DetailsFailed(val gameId: UUID, val error: Int) : Message
    data object NextVersions : Message
    data class EditVersion(val gameId: UUID, val title: String, val version: UUID) : Message
    data object EditFailedGeneration : Message
    data class Publish(val version: UUID) : Message
    data class Rate(val score: Int) : Message
    data object DismissNotice : Message
    data class Play(val request: PlayRequest) : Message
    data class GameReady(val request: PlayRequest, val files: LocalGame) : Message
    data class GameLoadFailed(val request: PlayRequest, val error: Int) : Message
    data object RetryGame : Message
}

sealed interface Command {
    data class LoadGame(val request: PlayRequest, val signedIn: Boolean) : Command
    data object CancelGameLoad : Command
    data class FetchFeed(val mine: Boolean, val query: String = "", val cursor: String? = null, val debounce: Boolean = false) : Command
    data class StoreAppearance(val value: Appearance) : Command
    data object FetchProfile : Command
    data class SignIn(val message: Message.Authenticate) : Command
    data class SaveProfile(val name: String) : Command
    data object LogOut : Command
    data class SendPrompt(val editor: Editor, val ownerId: UUID) : Command
    data class Poll(val ticket: GenerationTicket) : Command
    data class FetchDetails(val game: GameCardData, val viewerId: UUID?, val previous: Details? = null) : Command
    data class Publish(val game: GameCardData, val viewerId: UUID, val version: UUID) : Command
    data class Rate(val game: GameCardData, val viewerId: UUID, val score: Int) : Command
}

data class Transition(val state: AppState, val commands: List<Command> = emptyList())

fun update(state: AppState, message: Message): Transition = with(state) {
    fun transition(next: AppState = state, vararg commands: Command) = Transition(next, commands.toList())
    fun fetchFeed(mine: Boolean, nextPage: Boolean): Transition {
        val feed = if (mine) myGames else catalog
        if ((mine && profile == null) || (nextPage && (feed.loading || feed.cursor == null))) return transition()
        val next = feed.copy(loading = true, error = null)
        return transition(
            if (mine) copy(myGames = next) else copy(catalog = next),
            Command.FetchFeed(mine, if (mine) "" else query.trim(), feed.cursor.takeIf { nextPage }),
        )
    }

    when (message) {
        Message.Start -> transition(copy(catalog = catalog.copy(loading = true)), Command.FetchFeed(false), Command.FetchProfile)
        is Message.Select -> {
            val selected = copy(tab = message.tab, overlay = null)
            if (message.tab == Tab.GAMES && profile != null && !myGames.loading) {
                transition(selected.copy(myGames = myGames.copy(loading = true)), Command.FetchFeed(true))
            } else transition(selected)
        }
        Message.Back -> if (overlay == Overlay.PLAYER) {
            transition(copy(overlay = player?.from, player = null), Command.CancelGameLoad)
        } else transition(copy(overlay = if (overlay == Overlay.AUTH) authReturn else null,
            authReturn = null, profileError = null))
        is Message.Search -> transition(
            copy(query = message.query, catalog = Feed(loading = true)),
            Command.FetchFeed(false, message.query.trim(), debounce = true),
        )
        is Message.LoadFeed -> fetchFeed(message.mine, message.next)
        is Message.FeedLoaded -> {
            val previous = if (message.mine) myGames else catalog
            val items = if (message.append) (previous.games + message.page.items).distinctBy { it.id } else message.page.items
            val feed = Feed(items, message.page.nextCursor)
            transition(if (message.mine) copy(myGames = feed) else copy(catalog = feed))
        }
        is Message.FeedFailed -> transition(
            if (message.mine) copy(myGames = myGames.copy(loading = false, error = message.error))
            else copy(catalog = catalog.copy(loading = false, error = message.error)),
        )
        Message.OpenAppearance -> transition(copy(overlay = Overlay.APPEARANCE, draftAppearance = appearance))
        is Message.ChangeAppearance -> transition(copy(draftAppearance = message.value))
        Message.SaveAppearance -> transition(copy(appearance = draftAppearance, overlay = null), Command.StoreAppearance(draftAppearance))
        Message.OpenAuth -> transition(copy(overlay = Overlay.AUTH, authReturn = overlay, profileError = null))
        is Message.Authenticate -> if (profileBusy) transition() else transition(
            copy(profileBusy = true, profileError = null), Command.SignIn(message),
        )
        Message.ReloadProfile -> transition(copy(profileBusy = true, profileError = null), Command.FetchProfile)
        is Message.UpdateProfile -> if (profileBusy || profile == null) transition() else transition(
            copy(profileBusy = true, profileError = null), Command.SaveProfile(message.name),
        )
        Message.SignOut -> transition(
            copy(profile = null, profileBusy = true, profileError = null, myGames = Feed(), editor = Editor(),
                generation = null, details = null, overlay = null, authReturn = null),
            Command.LogOut,
        )
        is Message.ProfileLoaded -> {
            val signedIn = message.profile != null && overlay == Overlay.AUTH
            val next = copy(profile = message.profile, profileBusy = false, profileError = null,
                overlay = if (signedIn) authReturn else overlay, authReturn = if (signedIn) null else authReturn)
            val commands = mutableListOf<Command>()
            if (tab == Tab.GAMES && message.profile != null) commands += Command.FetchFeed(true)
            if (message.ticket != null && generation == null) commands += Command.Poll(message.ticket)
            if (signedIn && authReturn == Overlay.DETAILS && details != null) {
                commands += Command.FetchDetails(details.game, message.profile.id)
            }
            Transition(next.copy(
                myGames = if (tab == Tab.GAMES && message.profile != null) myGames.copy(loading = true) else myGames,
                generation = generation ?: message.ticket?.let { GenerationUi(it) },
            ), commands)
        }
        is Message.ProfileFailed -> transition(copy(profileBusy = false, profileError = message.error))
        is Message.ChangeTitle -> if (editor.sending || editor.gameId != null) transition() else transition(
            copy(editor = editor.copy(title = message.value.take(120), error = null)),
        )
        is Message.ChangePrompt -> if (editor.sending) transition() else transition(
            copy(editor = editor.copy(prompt = message.value.take(1000), key = null, error = null)),
        )
        is Message.Submit -> when {
            profile == null -> transition(copy(overlay = Overlay.AUTH, authReturn = overlay, profileError = null))
            editor.sending || editor.title.isBlank() || editor.prompt.isBlank() -> transition()
            generation?.let { it.status == GenerationStatus.Status.QUEUED || it.status == GenerationStatus.Status.RUNNING } == true ->
                transition(copy(overlay = Overlay.GENERATION))
            else -> {
                val pending = editor.copy(key = editor.key ?: message.key, sending = true, error = null)
                transition(copy(editor = pending), Command.SendPrompt(pending, profile.id))
            }
        }
        is Message.DraftCreated -> transition(copy(editor = editor.copy(gameId = message.gameId)))
        is Message.Accepted -> transition(
            copy(editor = Editor(), overlay = Overlay.GENERATION,
                generation = GenerationUi(message.ticket, message.status.status, message.status.resultVersionId)),
            Command.Poll(message.ticket),
        )
        is Message.SubmitFailed -> transition(copy(editor = editor.copy(sending = false, error = message.error)))
        Message.OpenGeneration -> if (generation == null) transition() else transition(copy(overlay = Overlay.GENERATION))
        Message.RetryPolling -> if (generation == null || generation.polling) transition() else transition(
            copy(generation = generation.copy(polling = true, error = null)), Command.Poll(generation.ticket),
        )
        is Message.Progress -> if (generation == null || generation.ticket.id != message.status.id) transition() else transition(
            copy(generation = generation.copy(
                status = message.status.status,
                versionId = message.status.resultVersionId,
                versionNumber = message.version,
                polling = message.status.status in setOf(GenerationStatus.Status.QUEUED, GenerationStatus.Status.RUNNING),
                error = if (message.status.error?.code == "GENERATION_TIMEOUT") R.string.generation_timeout else null,
            )),
        )
        is Message.PollFailed -> if (generation == null || generation.ticket.id != message.id) transition() else transition(
            copy(generation = generation.copy(polling = false, error = message.error)),
        )
        is Message.OpenGame -> transition(
            copy(overlay = Overlay.DETAILS, details = Details(message.game)),
            Command.FetchDetails(message.game, profile?.id),
        )
        is Message.DetailsLoaded -> if (details?.game?.id != message.value.game.id) transition() else transition(copy(details = message.value))
        is Message.DetailsFailed -> if (details?.game?.id != message.gameId) transition()
            else transition(copy(details = details.copy(busy = false, error = message.error)))
        Message.NextVersions -> if (details == null || details.cursor == null || details.busy) transition() else transition(
            copy(details = details.copy(busy = true, error = null)), Command.FetchDetails(details.game, profile?.id, details),
        )
        is Message.EditVersion -> if (editor.sending) transition() else transition(copy(
            tab = Tab.CREATE, overlay = null, editor = Editor(title = message.title, gameId = message.gameId, baseVersion = message.version),
        ))
        Message.EditFailedGeneration -> if (editor.sending || generation == null) transition() else transition(copy(
            tab = Tab.CREATE, overlay = null,
            editor = with(generation.ticket) { Editor(title, prompt, gameId, baseVersion) },
        ))
        is Message.Publish -> if (details == null || details.busy || profile?.id != details.game.ownerId) transition() else transition(
            copy(details = details.copy(busy = true, error = null)), Command.Publish(details.game, profile.id, message.version),
        )
        is Message.Rate -> when {
            profile == null -> transition(copy(overlay = Overlay.AUTH, authReturn = overlay, profileError = null))
            details == null || details.busy || details.game.ownerId == profile.id || details.game.publication == null -> transition()
            else -> transition(copy(details = details.copy(busy = true, error = null)), Command.Rate(details.game, profile.id, message.score))
        }
        Message.DismissNotice -> transition(copy(notice = null))
        is Message.Play -> transition(
            copy(overlay = Overlay.PLAYER, player = PlayerUi(message.request, overlay)),
            Command.LoadGame(message.request, profile != null),
        )
        is Message.GameReady -> if (player?.request != message.request) transition()
            else transition(copy(player = player.copy(packageFiles = message.files, error = null)))
        is Message.GameLoadFailed -> if (player?.request != message.request) transition()
            else transition(copy(player = player.copy(packageFiles = null, error = message.error)))
        Message.RetryGame -> if (player == null) transition() else transition(
            copy(player = player.copy(packageFiles = null, error = null)),
            Command.LoadGame(player.request, profile != null),
        )
    }
}
