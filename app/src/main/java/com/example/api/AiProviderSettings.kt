package com.example.api

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class ReceiptAnalysisProvider {
    GEMINI,
    OPENAI
}

data class AiProviderState(
    val provider: ReceiptAnalysisProvider = ReceiptAnalysisProvider.GEMINI,
    val openAiModel: String = DEFAULT_OPENAI_MODEL,
    val hasOpenAiKey: Boolean = false
) {
    companion object {
        const val DEFAULT_OPENAI_MODEL = "gpt-5.6"
    }
}

/**
 * Stores a user-entered OpenAI key encrypted with a non-exportable Android Keystore key.
 * The key is never exposed through [AiProviderState], logs, backups, or BuildConfig.
 */
object AiProviderSettings {
    private const val PREFS_NAME = "ai_provider_settings"
    private const val KEY_PROVIDER = "receipt_analysis_provider"
    private const val KEY_MODEL = "openai_model"
    private const val KEY_CIPHERTEXT = "openai_key_ciphertext"
    private const val KEY_IV = "openai_key_iv"
    private const val KEYSTORE_ALIAS = "steuer_assistent_openai_key_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun loadState(context: Context): AiProviderState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val provider = runCatching {
            ReceiptAnalysisProvider.valueOf(
                prefs.getString(KEY_PROVIDER, ReceiptAnalysisProvider.GEMINI.name)
                    ?: ReceiptAnalysisProvider.GEMINI.name
            )
        }.getOrDefault(ReceiptAnalysisProvider.GEMINI)
        val model = prefs.getString(KEY_MODEL, AiProviderState.DEFAULT_OPENAI_MODEL)
            ?.trim()
            .orEmpty()
            .ifBlank { AiProviderState.DEFAULT_OPENAI_MODEL }

        return AiProviderState(
            provider = provider,
            openAiModel = model,
            hasOpenAiKey = hasStoredOpenAiKey(context)
        )
    }

    fun saveSelection(
        context: Context,
        provider: ReceiptAnalysisProvider,
        model: String
    ): AiProviderState {
        require(provider != ReceiptAnalysisProvider.OPENAI || hasStoredOpenAiKey(context)) {
            "Für OpenAI muss zuerst ein API-Schlüssel gespeichert werden."
        }
        val normalizedModel = model.trim().ifBlank { AiProviderState.DEFAULT_OPENAI_MODEL }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROVIDER, provider.name)
            .putString(KEY_MODEL, normalizedModel)
            .apply()
        return loadState(context)
    }

    fun storeOpenAiKey(context: Context, rawKey: CharArray) {
        val key = rawKey.concatToString().trim()
        try {
            require(isPlausibleOpenAiKey(key)) {
                "Der OpenAI-API-Schlüssel hat kein gültiges Format."
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val encrypted = cipher.doFinal(key.toByteArray(StandardCharsets.UTF_8))

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .apply()
        } finally {
            rawKey.fill('\u0000')
        }
    }

    fun getOpenAiKey(context: Context): CharArray? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ciphertext = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getSecretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(
                cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)),
                StandardCharsets.UTF_8
            ).toCharArray()
        }.getOrElse {
            clearOpenAiKey(context)
            null
        }
    }

    fun clearOpenAiKey(context: Context): AiProviderState {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CIPHERTEXT)
            .remove(KEY_IV)
            .putString(KEY_PROVIDER, ReceiptAnalysisProvider.GEMINI.name)
            .apply()
        return loadState(context)
    }

    internal fun isPlausibleOpenAiKey(value: String): Boolean =
        value.startsWith("sk-") && value.length >= 24 && value.none(Char::isWhitespace)

    private fun hasStoredOpenAiKey(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_CIPHERTEXT) && prefs.contains(KEY_IV)
    }

    private fun getOrCreateSecretKey(): SecretKey =
        getSecretKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEYSTORE_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                )
            }
            .generateKey()

    private fun getSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
    }
}
