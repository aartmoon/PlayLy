package com.playly.app.ui

import android.annotation.SuppressLint
import android.os.Build
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewRenderProcess
import android.webkit.WebViewRenderProcessClient
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.playly.app.R
import com.playly.app.data.LocalGame
import java.io.File
import java.io.IOException
import java.util.UUID

@Composable
internal fun PlayerScreen(player: PlayerUi, send: (Message) -> Unit) {
    val files = player.packageFiles
    var restart by remember(files) { mutableIntStateOf(0) }
    var crashed by remember(files, restart) { mutableStateOf(false) }
    when {
        player.error != null -> Column(
            Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            ErrorText(player.error)
            if (player.error != R.string.player_unsupported) PrimaryButton(R.string.retry) { send(Message.RetryGame) }
        }
        crashed -> Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            ErrorText(R.string.player_failed)
            PrimaryButton(R.string.player_restart) { restart++ }
        }
        files == null -> LoadingIndicator()
        else -> key(files, restart) { GameWebView(files) { crashed = true } }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GameWebView(files: LocalGame, onError: () -> Unit) {
    val host = remember { "${UUID.randomUUID()}.playly.invalid" }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var webView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(lifecycle, webView) {
        val view = webView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    view?.onResume()
                    view?.resumeTimers()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    view?.onPause()
                    view?.pauseTimers()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.domStorageEnabled = false
                settings.useWideViewPort = true
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.mediaPlaybackRequiresUserGesture = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = LocalGameClient(host, files, onError)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) watchRenderer(onError)
                webView = this
                doOnLayout { loadUrl("https://$host/index.html") }
            }
        },
        onRelease = { view -> view.destroy() },
    )
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun WebView.watchRenderer(onError: () -> Unit) {
    webViewRenderProcessClient = object : WebViewRenderProcessClient() {
        override fun onRenderProcessUnresponsive(view: WebView, renderer: WebViewRenderProcess?) {
            if (renderer?.terminate() != true) onError()
        }
        override fun onRenderProcessResponsive(view: WebView, renderer: WebViewRenderProcess?) = Unit
    }
}

private class LocalGameClient(
    private val host: String,
    private val game: LocalGame,
    private val onError: () -> Unit,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
        val url = request.url
        if (request.method != "GET" || url.scheme != "https" || url.encodedAuthority != host || url.query != null) return blocked()
        val path = url.encodedPath?.removePrefix("/") ?: return blocked()
        val type = game.mediaTypes[path] ?: return blocked()
        return try {
            val stream = File(game.directory, path).inputStream()
            WebResourceResponse(type, if (type.startsWith("text/") || type == "application/javascript") "UTF-8" else null,
                200, "OK", mapOf(
                    "Content-Security-Policy" to "default-src 'none'; script-src 'self'; style-src 'self'; " +
                        "img-src 'self' data: blob:; font-src 'self'; media-src 'self' blob:; " +
                        "worker-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'",
                    "X-Content-Type-Options" to "nosniff",
                    "Cache-Control" to "no-store",
                ), stream)
        } catch (_: IOException) {
            blocked()
        }
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) onError()
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
        if (request.isForMainFrame) onError()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onError()
        return true
    }

    private fun blocked() = WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(),
        ByteArray(0).inputStream())
}
