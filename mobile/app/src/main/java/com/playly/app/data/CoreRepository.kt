package com.playly.app.data

import com.playly.app.BuildConfig
import com.playly.app.generated.apis.*
import com.playly.app.generated.infrastructure.*
import com.playly.app.generated.models.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// The generated response types flatten oneOf/allOf and lose these fields.
data class GameCardData(
    val id: UUID,
    val ownerId: UUID,
    val author: Author,
    val title: String,
    val description: String,
    val publication: Publication? = null,
    val rating: Rating,
) {
    data class Author(val displayName: String)
    data class Publication(val versionId: UUID)
    data class Rating(val average: Double? = null, val count: Long)
}

data class Page<T>(val items: List<T>, val nextCursor: String? = null)
data class PlayerProfile(val id: UUID, val displayName: String, val email: String)
data class ProjectVersion(val id: UUID, val number: Int)
data class GenerationStatus(
    val id: UUID,
    val gameId: UUID,
    val status: Status,
    val resultVersionId: UUID? = null,
    val error: Failure? = null,
) {
    enum class Status { QUEUED, RUNNING, SUCCEEDED, FAILED }
    data class Failure(val code: String)
}

data class CoreProblem(val code: String? = null)
class CoreError(val status: Int, val code: String?) : IOException()
class CoreNotConfigured : IOException()

@Singleton
class CoreRepository @Inject constructor(private val settings: LocalSettings) {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(12, TimeUnit.SECONDS).build()
    private val endpoint = BuildConfig.CORE_URL.trimEnd('/')
    private val transport = CoreTransport(endpoint, client)
    private val auth = AuthApi(endpoint, client)
    private val catalog = CatalogApi(endpoint, client)
    private val games = GamesApi(endpoint, client)
    private val generations = GenerationsApi(endpoint, client)
    private val profiles = ProfileApi(endpoint, client)
    private val versions = VersionsApi(endpoint, client)
    private val publications = PublicationsApi(endpoint, client)
    private val ratings = RatingsApi(endpoint, client)

    private val sessionLock = Any()
    private var session: AuthTokens? = null
    private var sessionLoaded = false
    private var sessionVersion = 0L

    private fun session(): AuthTokens? {
        if (!sessionLoaded) {
            session = settings.readSession()
            sessionLoaded = true
        }
        return session
    }

    private fun saveSession(value: AuthTokens?) {
        settings.writeSession(value)
        session = value
        sessionLoaded = true
    }

    fun catalog(query: String, cursor: String?): Page<GameCardData> =
        transport.data(catalog.listCatalogGamesRequestConfig(query.ifBlank { null }, cursor, 20))

    fun myGames(cursor: String?): Page<GameCardData> =
        authorized { transport.data(games.listMyGamesRequestConfig(cursor, 20), it) }

    fun profile(): PlayerProfile? = if (synchronized(sessionLock) { session() != null }) {
        try {
            authorized { transport.data<_, User>(profiles.getCurrentUserRequestConfig(), it).asProfile() }
        } catch (error: CoreError) {
            if (error.status == 401 && synchronized(sessionLock) { session == null }) null else throw error
        }
    } else null

    fun signIn(email: String, password: String, name: String?): PlayerProfile {
        synchronized(sessionLock) {
            val tokens: AuthTokens = if (name == null) {
                transport.data(auth.loginUserRequestConfig(LoginRequest(email, password)))
            } else {
                transport.data(auth.registerUserRequestConfig(RegisterRequest(email, password, name)))
            }
            saveSession(tokens)
            sessionVersion++
        }
        return requireNotNull(profile())
    }

    fun signOut() = synchronized(sessionLock) {
        val tokens = session()
        saveSession(null)
        sessionVersion++
        if (tokens != null) transport.call<RefreshTokenRequest, Unit>(
            auth.logoutUserRequestConfig(RefreshTokenRequest(tokens.refreshToken)),
        )
    }

    fun updateProfile(name: String): PlayerProfile =
        authorized { transport.data<_, User>(profiles.updateCurrentUserRequestConfig(ProfileUpdate(name)), it).asProfile() }

    fun createDraft(title: String, description: String): GameCardData =
        authorized { transport.data(games.createGameRequestConfig(GameMetadata(title, description)), it) }

    fun game(id: UUID, signedIn: Boolean): GameCardData =
        if (signedIn) authorized { transport.data(games.getGameRequestConfig(id), it) }
        else transport.data(games.getGameRequestConfig(id))

    fun versions(id: UUID, cursor: String?): Page<ProjectVersion> = authorized {
        val page = transport.data<_, VersionPage>(versions.listGameVersionsRequestConfig(id, cursor, 20), it)
        Page(page.items.map { item -> ProjectVersion(item.id, item.number) }, page.nextCursor)
    }

    fun version(id: UUID, version: UUID): ProjectVersion = authorized {
        val value = transport.data<_, GameVersion>(versions.getGameVersionRequestConfig(id, version), it)
        ProjectVersion(value.id, value.number)
    }

    private fun User.asProfile() = PlayerProfile(id, displayName, email)

    fun publish(id: UUID, version: UUID) {
        authorized { transport.data<_, Publication>(publications.publishGameRequestConfig(id, PublishGameRequest(version)), it) }
    }

    fun rate(id: UUID, score: Int) {
        authorized { transport.data<_, Rating>(ratings.rateGameRequestConfig(id, RateGameRequest(score)), it) }
    }

    fun submit(id: UUID, key: UUID, prompt: String, base: UUID?): GenerationStatus =
        authorized { transport.data(
            generations.createGenerationRequestConfig(id, key.toString(), GenerationRequest(prompt, base)), it,
        ) }

    fun status(id: UUID): GenerationStatus =
        authorized { transport.data(generations.getGenerationRequestConfig(id), it) }

    private inline fun <T> authorized(operation: (String) -> T): T {
        val (original, version) = synchronized(sessionLock) {
            (session() ?: throw CoreError(401, null)) to sessionVersion
        }
        return try {
            operation(original.accessToken)
        } catch (error: CoreError) {
            if (error.status != 401) throw error
            val refreshed = synchronized(sessionLock) {
                if (sessionVersion != version) throw CoreError(401, null)
                val current = session ?: throw CoreError(401, null)
                if (current.refreshToken != original.refreshToken) current
                else try {
                    transport.data<_, AuthTokens>(auth.refreshTokensRequestConfig(RefreshTokenRequest(current.refreshToken)))
                        .also(::saveSession)
                } catch (failure: CoreError) {
                    if (failure.status == 401) saveSession(null)
                    throw failure
                }
            }
            operation(refreshed.accessToken)
        }
    }
}

private class CoreTransport(endpoint: String, client: OkHttpClient) : ApiClient(endpoint, client) {
    private val endpoint = endpoint.toHttpUrlOrNull()
    private val problemAdapter = Serializer.moshi.adapter(CoreProblem::class.java)

    inline fun <reified I, reified T> data(config: RequestConfig<I>, token: String? = null): T =
        call<I, T>(config, token) ?: throw IOException("Missing response body")

    inline fun <reified I, reified T> call(config: RequestConfig<I>, token: String? = null): T? {
        val url = endpoint ?: throw CoreNotConfigured()
        if ((!BuildConfig.DEBUG && !url.isHttps) || url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw CoreNotConfigured()
        }
        if (token != null) config.headers["Authorization"] = "Bearer $token"
        return when (val response = request<I, T>(config)) {
            is Success -> response.data
            is ClientError -> {
                val problem = (response.body as? String)?.let { runCatching { problemAdapter.fromJson(it) }.getOrNull() }
                throw CoreError(response.statusCode, problem?.code)
            }
            else -> throw CoreError(response.statusCode, null)
        }
    }
}
