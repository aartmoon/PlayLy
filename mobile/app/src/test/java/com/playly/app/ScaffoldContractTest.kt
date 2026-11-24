package com.playly.app

import com.playly.app.data.GameCardData
import com.playly.app.data.GenerationStatus
import com.playly.app.data.LocalGame
import com.playly.app.data.Page
import com.playly.app.data.PlayerProfile
import com.playly.app.generated.infrastructure.Serializer
import com.playly.app.ui.AppState
import com.playly.app.ui.Command
import com.playly.app.ui.Editor
import com.playly.app.ui.Message
import com.playly.app.ui.Overlay
import com.playly.app.ui.PlayRequest
import com.playly.app.ui.update
import com.squareup.moshi.adapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.UUID

class ScaffoldContractTest {
    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun decodesPublishedAndDraftCards() {
        val adapter = Serializer.moshi.adapter<Page<GameCardData>>()
        val id = UUID.randomUUID()
        val version = UUID.randomUUID()
        val fields = """
            "id":"$id","ownerId":"$id","author":{"displayName":"Player"},
            "title":"Night city","description":"","rating":{"average":null,"count":0}
        """.trimIndent()
        val page = requireNotNull(adapter.fromJson("""
            {"items":[
                {$fields,"status":"PUBLISHED","publication":{"versionId":"$version"}},
                {$fields,"status":"DRAFT","publication":null}
            ], "nextCursor":null}
        """))
        assertEquals(version, page.items[0].publication?.versionId)
        assertNull(page.items[1].publication)
        assertNull(page.items[1].rating.average)
    }

    @Test
    fun decodesNonterminalResponseWithoutResultOrError() {
        val id = UUID.randomUUID()
        val adapter = Serializer.moshi.adapter(GenerationStatus::class.java)
        val queued = requireNotNull(adapter.fromJson("""{"id":"$id","gameId":"$id","status":"QUEUED"}"""))
        assertEquals(GenerationStatus.Status.QUEUED, queued.status)
        assertNull(queued.resultVersionId)
        assertNull(queued.error)
    }

    @Test
    fun retryReusesDraftAndIdempotencyKey() {
        val originalKey = UUID.randomUUID()
        val gameId = UUID.randomUUID()
        val initial = AppState(
            profile = PlayerProfile(UUID.randomUUID(), "Player", "demo@example.test"),
            editor = Editor(title = "Night city", prompt = "Collect coins"),
        )
        val submitting = update(initial, Message.Submit(originalKey)).state
        val created = update(submitting, Message.DraftCreated(gameId)).state
        val failed = update(created, Message.SubmitFailed(R.string.connection_error)).state
        val retry = update(failed, Message.Submit(UUID.randomUUID())).commands.single() as Command.SendPrompt
        assertEquals(gameId, retry.editor.gameId)
        assertEquals(originalKey, retry.editor.key)
    }

    @Test
    fun playerLoadIsDiscardedAfterReturningToSourceScreen() {
        val request = PlayRequest(UUID.randomUUID(), UUID.randomUUID())
        val open = update(AppState(overlay = Overlay.DETAILS), Message.Play(request))
        assertEquals(Overlay.PLAYER, open.state.overlay)
        assertEquals(Command.LoadGame(request, false), open.commands.single())

        val close = update(open.state, Message.Back)
        assertEquals(Overlay.DETAILS, close.state.overlay)
        assertEquals(Command.CancelGameLoad, close.commands.single())
        val late = Message.GameReady(request, LocalGame(File("cache"), emptyMap()))
        assertEquals(close.state, update(close.state, late).state)
    }
}
