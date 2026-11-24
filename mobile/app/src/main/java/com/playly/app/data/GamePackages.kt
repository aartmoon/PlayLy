package com.playly.app.data

import android.content.Context
import com.playly.app.BuildConfig
import com.playly.app.generated.models.GamePackage
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class LocalGame(val directory: File, val mediaTypes: Map<String, String>)

class UnsupportedGamePackage : IOException()
class InvalidGamePackage : IOException()

@Singleton
class GamePackages @Inject constructor(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(25, TimeUnit.SECONDS).build()
    private val path = Regex("(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-][A-Za-z0-9_.-]*")
    private val sha = Regex("[a-f0-9]{64}")
    private val types = mapOf(
        "html" to "text/html", "js" to "application/javascript", "css" to "text/css",
        "json" to "application/json", "png" to "image/png", "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg", "webp" to "image/webp", "ogg" to "audio/ogg",
        "mp3" to "audio/mpeg", "wav" to "audio/wav", "woff2" to "font/woff2",
    )

    @Synchronized
    fun load(value: GamePackage, game: UUID, version: UUID): LocalGame {
        if (value.gameSpecVersion != "2.0" || value.runtimeVersion != "2.0.0") throw UnsupportedGamePackage()
        if (value.gameId != game || value.versionId != version || value.assets.size > 512) throw InvalidGamePackage()
        val spec = value.gameSpec
        if (spec.path.value != "game.json" || spec.sizeBytes !in 1..16384) throw InvalidGamePackage()
        val files = listOf(Download(spec.path.value, spec.url.toString(), spec.mediaType.value, spec.sizeBytes, spec.sha256)) +
            value.assets.map { Download(it.path, it.url.toString(), it.mediaType, it.sizeBytes, it.sha256) }
        val media = mutableMapOf<String, String>()
        var remaining = 16L * 1024 * 1024 + 16384
        for (file in files) {
            if (file.path.length > 1024 || !path.matches(file.path) || !sha.matches(file.sha256) ||
                types[file.path.substringAfterLast('.', "")] != file.mediaType ||
                media.put(file.path, file.mediaType) != null || file.size !in 1..remaining
            ) throw InvalidGamePackage()
            remaining -= file.size
        }
        if (media["index.html"] != "text/html" || media.keys.any { key ->
                key.indices.any { key[it] == '/' && key.substring(0, it) in media }
            }) throw InvalidGamePackage()
        val root = File(context.cacheDir, "games")
        if (!root.isDirectory && !root.mkdirs()) throw IOException("Cannot create game cache")
        val destination = File(root, "$game-$version")
        if (destination.isDirectory && files.all { checkFile(File(destination, it.path), it) }) {
            checkDescriptor(destination)
            return LocalGame(destination, media)
        }
        val staging = File(root, ".download-${UUID.randomUUID()}")
        if (!staging.mkdir()) throw IOException("Cannot stage game cache")
        try {
            files.forEach { download(it, File(staging, it.path)) }
            checkDescriptor(staging)
            if (destination.exists() && !destination.deleteRecursively()) throw IOException("Cannot replace game cache")
            if (!staging.renameTo(destination)) throw IOException("Cannot activate game cache")
            return LocalGame(destination, media)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun checkDescriptor(directory: File) {
        val descriptor = try {
            JSONObject(File(directory, "game.json").readText())
        } catch (_: org.json.JSONException) {
            throw InvalidGamePackage()
        }
        if (descriptor.optString("format") != "phaser-web" || descriptor.optString("entryPoint") != "index.html" ||
            descriptor.optString("packageSchemaVersion") != "2.0" || descriptor.optString("runtimeVersion") != "2.0.0"
        ) throw UnsupportedGamePackage()
    }

    private fun checkFile(file: File, entry: Download): Boolean {
        if (!file.isFile || file.length() != entry.size) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex() == entry.sha256
    }

    private fun download(entry: Download, target: File) {
        val url = entry.url.toHttpUrlOrNull() ?: throw InvalidGamePackage()
        if (url.username.isNotEmpty() || url.password.isNotEmpty() ||
            (!url.isHttps && (!BuildConfig.DEBUG || url.host !in setOf("10.0.2.2", "localhost", "127.0.0.1")))
        ) throw InvalidGamePackage()
        target.parentFile?.mkdirs()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Game file is unavailable")
            val body = response.body ?: throw InvalidGamePackage()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var remaining = entry.size
                    while (remaining > 0) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read < 0) throw InvalidGamePackage()
                        output.write(buffer, 0, read)
                        remaining -= read
                    }
                    if (input.read() != -1) throw InvalidGamePackage()
                }
            }
        }
        if (!checkFile(target, entry)) throw InvalidGamePackage()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private data class Download(val path: String, val url: String, val mediaType: String, val size: Long, val sha256: String)
}
