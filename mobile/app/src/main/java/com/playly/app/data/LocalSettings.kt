package com.playly.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.playly.app.generated.infrastructure.Serializer
import com.playly.app.generated.models.AuthTokens
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { LIGHT, DARK, CUSTOM }

data class Appearance(
    val mode: ThemeMode = ThemeMode.DARK,
    val accent: Int = 0xFFC6FF4D.toInt(),
    val background: Int = DEFAULT_CUSTOM_BACKGROUND,
) {
    companion object {
        const val DEFAULT_CUSTOM_BACKGROUND = 0xFF10110F.toInt()
        const val LEGACY_LIGHT_BACKGROUND = 0xFFFAFBF4.toInt()
    }
}

data class GenerationTicket(
    val id: UUID,
    val gameId: UUID,
    val title: String,
    val ownerId: UUID,
    val prompt: String = "",
    val baseVersion: UUID? = null,
)

@Singleton
class LocalSettings @Inject constructor(context: Context) {
    private val preferences = context.getSharedPreferences("playly_settings", Context.MODE_PRIVATE)
    private val tokens = Serializer.moshi.adapter(AuthTokens::class.java)
    private val ticket = Serializer.moshi.adapter(GenerationTicket::class.java)

    fun readAppearance() = Appearance(
        mode = ThemeMode.entries.firstOrNull { it.name == preferences.getString("mode", null) } ?: ThemeMode.DARK,
        accent = preferences.getInt("accent", Appearance().accent),
        background = preferences.getInt(
            "background",
            if (preferences.getBoolean("custom_dark", true)) Appearance.DEFAULT_CUSTOM_BACKGROUND
            else Appearance.LEGACY_LIGHT_BACKGROUND,
        ),
    )

    fun writeAppearance(value: Appearance) {
        preferences.edit().putString("mode", value.mode.name)
            .putInt("accent", value.accent).putInt("background", value.background).remove("custom_dark").apply()
    }

    fun readTicket(ownerId: UUID): GenerationTicket? =
        preferences.getString("ticket_$ownerId", null)?.let { runCatching { ticket.fromJson(it) }.getOrNull() }

    fun writeTicket(value: GenerationTicket) {
        preferences.edit().putString("ticket_${value.ownerId}", ticket.toJson(value)).apply()
    }

    fun readSession(): AuthTokens? {
        val encoded = preferences.getString("session", null) ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
            tokens.fromJson(cipher.doFinal(payload.copyOfRange(12, payload.size)).decodeToString())
        }.getOrElse {
            preferences.edit().remove("session").apply()
            null
        }
    }

    fun writeSession(value: AuthTokens?) {
        val edit = preferences.edit()
        if (value == null) {
            edit.remove("session")
        } else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val payload = cipher.iv + cipher.doFinal(tokens.toJson(value).encodeToByteArray())
            edit.putString("session", Base64.encodeToString(payload, Base64.NO_WRAP))
        }
        check(edit.commit()) { "Could not save session" }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "playly_session"
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }
}
