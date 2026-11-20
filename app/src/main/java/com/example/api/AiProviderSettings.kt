
package com.example.api

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.data.OfflineBackupCredentialSection
import com.example.data.OfflineBackupKeyEnvelope
import com.example.data.OfflineBackupValidationException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.security.GeneralSecurityException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.ensureActive

enum class ReceiptAnalysisProvider {
    GEMINI,
    OPENAI
}

data class AiProviderState(
    val provider: ReceiptAnalysisProvider = ReceiptAnalysisProvider.GEMINI,
    val openAiModel: String = DEFAULT_OPENAI_MODEL,
    val hasOpenAiKey: Boolean = false,
    val hasGeminiKey: Boolean = false,
    val hasGoogleRoutesKey: Boolean = false
) {
    companion object {
        const val DEFAULT_OPENAI_MODEL = "gpt-5.6"
    }
}

/**
 * Stores user-entered provider keys encrypted with non-exportable Android Keystore keys.
 * Plaintext keys are never exposed through [AiProviderState], logs, backups, or BuildConfig.
 */
object AiProviderSettings {
    private const val PREFS_NAME = "ai_provider_settings"
    private const val KEY_PROVIDER = "receipt_analysis_provider"
    private const val KEY_MODEL = "openai_model"
    private const val KEY_CIPHERTEXT = "openai_key_ciphertext"
    private const val KEY_IV = "openai_key_iv"
    private const val KEY_GEMINI_CIPHERTEXT = "gemini_key_ciphertext"
    private const val KEY_GEMINI_IV = "gemini_key_iv"
    private const val KEY_GOOGLE_ROUTES_CIPHERTEXT = "google_routes_key_ciphertext"
    private const val KEY_GOOGLE_ROUTES_IV = "google_routes_key_iv"
    private const val KEYSTORE_ALIAS = "steuer_assistent_openai_key_v1"
    private const val GEMINI_KEYSTORE_ALIAS = "steuer_assistent_gemini_key_v1"
    private const val GOOGLE_ROUTES_KEYSTORE_ALIAS = "steuer_assistent_google_routes_key_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun loadState(context: Context): AiProviderState {
        val prefs = com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME)
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
            hasOpenAiKey = hasStoredOpenAiKey(context),
            hasGeminiKey = hasStoredGeminiKey(context),
            hasGoogleRoutesKey = hasStoredGoogleRoutesKey(context)
        )
    }

    fun saveSelection(
        context: Context,
        provider: ReceiptAnalysisProvider,
        model: String
    ): AiProviderState {
        require(
            (provider != ReceiptAnalysisProvider.OPENAI || hasStoredOpenAiKey(context)) &&
                (provider != ReceiptAnalysisProvider.GEMINI || hasStoredGeminiKey(context))
        ) {
            "Für OpenAI muss zuerst ein API-Schlüssel gespeichert werden."
        }
        val normalizedModel = model.trim().ifBlank { AiProviderState.DEFAULT_OPENAI_MODEL }
        com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME)
            .edit()
            .putString(KEY_PROVIDER, provider.name)
            .putString(KEY_MODEL, normalizedModel)
            .apply()
        return loadState(context)
    }

    fun storeOpenAiKey(context: Context, rawKey: CharArray) {
        try {
            com.example.data.OfflineBackupCoordinator.shared.beginWrite().use {
                val key = rawKey.concatToString().trim()
                require(isPlausibleOpenAiKey(key)) {
                    "Der OpenAI-API-Schlüssel hat kein gültiges Format."
                }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
                val encrypted = cipher.doFinal(key.toByteArray(StandardCharsets.UTF_8))

                com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME)
                    .edit()
                    .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                    .apply()
            }
        } finally {
            rawKey.fill('\u0000')
        }
    }

    fun getOpenAiKey(context: Context): CharArray? {
        val prefs = com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME)
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

    fun storeGeminiKey(context: Context, rawKey: CharArray) {
        try {
            com.example.data.OfflineBackupCoordinator.shared.beginWrite().use {
                val key = rawKey.concatToString().trim()
                require(isPlausibleGeminiKey(key)) {
                    "Der Gemini-API-Schlüssel hat kein gültiges Format."
                }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateGeminiSecretKey())
                val encrypted = cipher.doFinal(key.toByteArray(StandardCharsets.UTF_8))
                com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME)
                    .edit()
                    .putString(KEY_GEMINI_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(KEY_GEMINI_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                    .apply()
            }
        } finally {
            rawKey.fill('\u0000')
        }
    }

    fun getGeminiKey(context: Context): CharArray? {
        val prefs = com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME)
        val ciphertext = prefs.getString(KEY_GEMINI_CIPHERTEXT, null) ?: return null
        val iv = prefs.getString(KEY_GEMINI_IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getGeminiSecretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), StandardCharsets.UTF_8).toCharArray()
        }.getOrElse {
            clearGeminiKey(context)
            null
        }
    }

    fun storeGoogleRoutesKey(context: Context, rawKey: CharArray) {
        try {
            com.example.data.OfflineBackupCoordinator.shared.beginWrite().use {
                val key = rawKey.concatToString().trim()
                require(isPlausibleGoogleRoutesKey(key)) { "Der Google-Routes-API-Schlüssel hat kein gültiges Format." }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateGoogleRoutesSecretKey())
                val encrypted = cipher.doFinal(key.toByteArray(StandardCharsets.UTF_8))
                com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME).edit()
                    .putString(KEY_GOOGLE_ROUTES_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(KEY_GOOGLE_ROUTES_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                    .apply()
            }
        } finally {
            rawKey.fill('\u0000')
        }
    }

    fun getGoogleRoutesKey(context: Context): CharArray? {
        val prefs = com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME)
        val ciphertext = prefs.getString(KEY_GOOGLE_ROUTES_CIPHERTEXT, null) ?: return null
        val iv = prefs.getString(KEY_GOOGLE_ROUTES_IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getGoogleRoutesSecretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), StandardCharsets.UTF_8).toCharArray()
        }.getOrElse {
            clearGoogleRoutesKey(context)
            null
        }
    }

    fun clearOpenAiKey(context: Context): AiProviderState {
        com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME)
            .edit()
            .remove(KEY_CIPHERTEXT)
            .remove(KEY_IV)
            .putString(KEY_PROVIDER, ReceiptAnalysisProvider.GEMINI.name)
            .apply()
        return loadState(context)
    }

    fun clearGeminiKey(context: Context): AiProviderState {
        com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME)
            .edit()
            .remove(KEY_GEMINI_CIPHERTEXT)
            .remove(KEY_GEMINI_IV)
            .apply()
        return loadState(context)
    }

    fun clearGoogleRoutesKey(context: Context): AiProviderState {
        com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME).edit()
            .remove(KEY_GOOGLE_ROUTES_CIPHERTEXT)
            .remove(KEY_GOOGLE_ROUTES_IV)
            .apply()
        return loadState(context)
    }

    internal fun isPlausibleOpenAiKey(value: String): Boolean =
        value.startsWith("sk-") && value.length >= 24 && value.none(Char::isWhitespace)

    // Google AI Studio now issues both legacy AIza keys and newer auth keys.
    // Key prefixes are not a stable validation mechanism; reject only clearly malformed input.
    internal fun isPlausibleGeminiKey(value: String): Boolean =
        value.length >= 16 && value.none(Char::isWhitespace)

    internal fun isPlausibleGoogleRoutesKey(value: String): Boolean =
        value.length >= 16 && value.none(Char::isWhitespace)

    /** Read-only export: never calls getters that clear damaged entries or creates device keys. */
    internal fun exportBackupCredentials(context: Context, generation: OfflineBackupKeyEnvelope.Generation,
        consent: OfflineBackupCredentialSection.Consent = OfflineBackupCredentialSection.Consent(),
        trigger: OfflineBackupCredentialSection.Trigger = OfflineBackupCredentialSection.Trigger.MANUAL): ByteArray? {
        if (!consent.allows(trigger)) return null
        val values = linkedMapOf<OfflineBackupCredentialSection.Provider, CharArray>()
        try {
            val snapshot = com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME).all
            readBackupKey(snapshot, KEY_CIPHERTEXT, KEY_IV, ::getSecretKey)?.let {
                values[OfflineBackupCredentialSection.Provider.OPENAI] = it
            }
            readBackupKey(snapshot, KEY_GEMINI_CIPHERTEXT, KEY_GEMINI_IV, ::getGeminiSecretKey)?.let {
                values[OfflineBackupCredentialSection.Provider.GEMINI] = it
            }
            readBackupKey(snapshot, KEY_GOOGLE_ROUTES_CIPHERTEXT, KEY_GOOGLE_ROUTES_IV, ::getGoogleRoutesSecretKey)?.let {
                values[OfflineBackupCredentialSection.Provider.GOOGLE_ROUTES] = it
            }
            return OfflineBackupCredentialSection.seal(generation, values, consent, trigger)
        } catch (_: GeneralSecurityException) { backupCredentialFailure() }
        catch (_: CharacterCodingException) { backupCredentialFailure() }
        catch (_: IllegalArgumentException) { backupCredentialFailure() }
        catch (_: IllegalStateException) { backupCredentialFailure() }
        finally { values.values.forEach { it.fill('\u0000') } }
    }

    /** Import only explicitly selected archive credentials; the journal holds their prior ciphertexts. */
    internal suspend fun importBackupCredentials(context: Context,
        token: com.example.data.OfflineBackupCoordinator.Maintenance,
        entry: com.example.data.OfflineRestoreJournal.Entry,
        archive: com.example.data.OfflineBackupRestoreInspector.Prepared) {
        var changed = false
        try {
            com.example.data.OfflineBackupCoordinator.shared.requireMaintenance(token)
            require(entry.phase == com.example.data.OfflineRestoreJournal.Phase.PREFERENCES)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { token.suspending {
                val index = com.example.data.OfflineRestoreRollbackVerifier.verifyIndex(context, entry)
                val previous = index.credentials ?: return@suspending // No explicit credential import.
                com.example.data.OfflineRestoreDatabaseVerifier.verify(context, entry)
                val plan = com.example.data.OfflineRestoreImportedFiles.verifyInstalled(context, token, entry)
                com.example.data.OfflineBackupRoomProbe.prepare(context, archive.contents, archive.generation).use { probe ->
                    require(com.example.data.OfflineRestoreFilePlan.prepare(context, entry.operationId, probe.manifest, requireUnused = false) == plan)
                    val section = requireNotNull(probe.manifest.credentials)
                    val sealed = requireNotNull(archive.contents.parts.getValue(section.entry).inMemory)
                    OfflineBackupCredentialSection.open(archive.generation, sealed).use { opened ->
                        require(opened.values.keys == probe.manifest.credentialProviders)
                        val expected = opened.values.mapValues { it.value.copyOf() }
                        try {
                            require(com.example.data.OfflineRestoreJournal(context).load() == entry)
                            for ((provider, value) in opened.values) {
                                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                changed = true
                                when (provider) {
                                    OfflineBackupCredentialSection.Provider.OPENAI -> storeOpenAiKey(context, value)
                                    OfflineBackupCredentialSection.Provider.GEMINI -> storeGeminiKey(context, value)
                                    OfflineBackupCredentialSection.Provider.GOOGLE_ROUTES -> storeGoogleRoutesKey(context, value)
                                }
                            }
                            // The existing setters use apply(); a synchronous commit flushes the resulting state.
                            check(com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME).edit().commit())
                            val after = captureRollbackCiphertexts(context)
                            (after.keys - expected.keys).forEach { require(after[it] == previous.state[it]) }
                            val roundTrip = requireNotNull(exportBackupCredentials(context, archive.generation,
                                OfflineBackupCredentialSection.Consent(manual = true)))
                            try { OfflineBackupCredentialSection.open(archive.generation, roundTrip).use { restored ->
                                expected.forEach { (provider, value) -> require(restored.values[provider]?.contentEquals(value) == true) }
                            } } finally { roundTrip.fill(0) }
                            require(com.example.data.OfflineRestoreRollbackVerifier.verifyIndex(context, entry) == index)
                            require(com.example.data.OfflineRestoreImportedFiles.verifyInstalled(context, token, entry) == plan)
                            require(com.example.data.OfflineRestoreJournal(context).load() == entry)
                        } finally { expected.values.forEach { it.fill('\u0000') } }
                    }
                }
            } }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { if (changed) rollbackWriteFailure() else throw error }
        catch (_: GeneralSecurityException) { rollbackWriteFailure() }
        catch (_: IllegalArgumentException) { rollbackWriteFailure() }
        catch (_: IllegalStateException) { rollbackWriteFailure() }
        catch (_: NoSuchElementException) { rollbackWriteFailure() }
        catch (_: SecurityException) { rollbackWriteFailure() }
    }

    /** Existing device ciphertext only; validation never clears entries or creates keys. */
    internal data class RollbackCiphertext(val ciphertext: String, val iv: String)

    internal fun captureRollbackCiphertexts(context: Context): Map<OfflineBackupCredentialSection.Provider, RollbackCiphertext?> = try {
        val snapshot = com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME).all
        val state = rollbackFields().mapValues { (_, names) ->
            val (ciphertext, iv) = names
            if (!snapshot.containsKey(ciphertext) && !snapshot.containsKey(iv)) null
            else RollbackCiphertext(snapshot[ciphertext] as? String ?: throw IllegalArgumentException(),
                snapshot[iv] as? String ?: throw IllegalArgumentException())
        }
        validateRollbackCiphertexts(state)
        state
    } catch (_: GeneralSecurityException) { rollbackCredentialFailure() }
    catch (_: CharacterCodingException) { rollbackCredentialFailure() }
    catch (_: IllegalArgumentException) { rollbackCredentialFailure() }
    catch (_: IllegalStateException) { rollbackCredentialFailure() }
    catch (_: SecurityException) { rollbackCredentialFailure() }

    internal fun validateRollbackCiphertexts(state: Map<OfflineBackupCredentialSection.Provider, RollbackCiphertext?>) {
        try {
            val fields = rollbackFields(); require(state.keys == fields.keys)
            val snapshot = mutableMapOf<String, String>()
            state.forEach { (provider, value) -> if (value != null) {
                val (ciphertext, iv) = fields.getValue(provider)
                snapshot[ciphertext] = value.ciphertext; snapshot[iv] = value.iv
            } }
            readBackupKey(snapshot, KEY_CIPHERTEXT, KEY_IV, ::getSecretKey)?.fill('\u0000')
            readBackupKey(snapshot, KEY_GEMINI_CIPHERTEXT, KEY_GEMINI_IV, ::getGeminiSecretKey)?.fill('\u0000')
            readBackupKey(snapshot, KEY_GOOGLE_ROUTES_CIPHERTEXT, KEY_GOOGLE_ROUTES_IV, ::getGoogleRoutesSecretKey)?.fill('\u0000')
        } catch (_: GeneralSecurityException) { rollbackCredentialFailure() }
        catch (_: CharacterCodingException) { rollbackCredentialFailure() }
        catch (_: IllegalArgumentException) { rollbackCredentialFailure() }
        catch (_: IllegalStateException) { rollbackCredentialFailure() }
        catch (_: SecurityException) { rollbackCredentialFailure() }
    }
    /** Replays only journal-bound encrypted records; no key is created or deleted. */
    internal suspend fun restoreRollbackCiphertexts(
        context: Context,
        token: com.example.data.OfflineBackupCoordinator.Maintenance,
        entry: com.example.data.OfflineRestoreJournal.Entry
    ) {
        try {
            com.example.data.OfflineBackupCoordinator.shared.requireMaintenance(token)
            require(entry.phase == com.example.data.OfflineRestoreJournal.Phase.ROLLBACK)
            token.suspending {
                val checked = com.example.data.OfflineRestoreRollbackVerifier.verifyIndex(context, entry)
                val credentials = checked.credentials ?: return@suspending
                require(com.example.data.OfflineRestoreJournal(context).load() == entry)
                validateRollbackCiphertexts(credentials.state)
                val editor = com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME).edit()
                rollbackFields().forEach { (provider, names) ->
                    val value = credentials.state.getValue(provider)
                    if (value == null) { editor.remove(names.first); editor.remove(names.second) }
                    else { editor.putString(names.first, value.ciphertext); editor.putString(names.second, value.iv) }
                }
                check(editor.commit())
                try { require(captureRollbackCiphertexts(context) == credentials.state) }
                catch (_: OfflineBackupValidationException) { rollbackWriteFailure() }
                require(com.example.data.OfflineRestoreJournal(context).load() == entry)
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IllegalArgumentException) { rollbackWriteFailure() }
        catch (_: IllegalStateException) { rollbackWriteFailure() }
        catch (_: SecurityException) { rollbackWriteFailure() }
    }
    private fun rollbackWriteFailure(): Nothing = throw OfflineBackupValidationException(
        "Der Rückfall der API-Schlüssel konnte nicht abgeschlossen werden. Der Rückfall bleibt gesperrt und kann erneut versucht werden.")
    private fun rollbackFields() = mapOf(
        OfflineBackupCredentialSection.Provider.OPENAI to (KEY_CIPHERTEXT to KEY_IV),
        OfflineBackupCredentialSection.Provider.GEMINI to (KEY_GEMINI_CIPHERTEXT to KEY_GEMINI_IV),
        OfflineBackupCredentialSection.Provider.GOOGLE_ROUTES to (KEY_GOOGLE_ROUTES_CIPHERTEXT to KEY_GOOGLE_ROUTES_IV))
    private fun rollbackCredentialFailure(): Nothing = throw OfflineBackupValidationException(
        "Die vorhandenen API-Schlüssel können nicht sicher für einen Rückfall geprüft werden. Die Schlüssel wurden nicht verändert.")

    private fun readBackupKey(snapshot: Map<String, *>, ciphertextName: String, ivName: String,
        loadKey: () -> SecretKey?): CharArray? {
        if (!snapshot.containsKey(ciphertextName) && !snapshot.containsKey(ivName)) return null
        val ciphertext = snapshot[ciphertextName] as? String
        val iv = snapshot[ivName] as? String
        require(ciphertext != null && ciphertext.length in 1..(24 * 1024) && iv != null && iv.length == 16)
        val encrypted = Base64.decode(ciphertext, Base64.NO_WRAP)
        val nonce = Base64.decode(iv, Base64.NO_WRAP)
        require(encrypted.size in 16..(16 * 1024 + 16) && nonce.size == 12)
        val key = loadKey() ?: backupCredentialFailure()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val plaintext = cipher.doFinal(encrypted)
        try {
            val decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(plaintext))
            try {
                require(decoded.remaining() in 1..4096)
                return CharArray(decoded.remaining()).also { decoded.get(it) }
            } finally { if (decoded.hasArray()) decoded.array().fill('\u0000') }
        } finally { plaintext.fill(0) }
    }

    private fun backupCredentialFailure(): Nothing = throw OfflineBackupValidationException(
        "Persönliche API-Schlüssel konnten nicht sicher exportiert werden. Gespeicherte Einstellungen wurden nicht verändert.")

    private fun hasStoredOpenAiKey(context: Context): Boolean {
        val prefs = com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME)
        return prefs.contains(KEY_CIPHERTEXT) && prefs.contains(KEY_IV)
    }

    private fun hasStoredGeminiKey(context: Context): Boolean {
        val prefs = com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME)
        return prefs.contains(KEY_GEMINI_CIPHERTEXT) && prefs.contains(KEY_GEMINI_IV)
    }

    private fun hasStoredGoogleRoutesKey(context: Context): Boolean {
        val prefs = com.example.data.PersistentPreferenceInventory.open(context, PREFS_NAME)
        return prefs.contains(KEY_GOOGLE_ROUTES_CIPHERTEXT) && prefs.contains(KEY_GOOGLE_ROUTES_IV)
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

    private fun getOrCreateGeminiSecretKey(): SecretKey =
        getGeminiSecretKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        GEMINI_KEYSTORE_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                )
            }
            .generateKey()

    private fun getGeminiSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(GEMINI_KEYSTORE_ALIAS, null) as? SecretKey
    }

    private fun getOrCreateGoogleRoutesSecretKey(): SecretKey =
        getGoogleRoutesSecretKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        GOOGLE_ROUTES_KEYSTORE_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                )
            }.generateKey()

    private fun getGoogleRoutesSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(GOOGLE_ROUTES_KEYSTORE_ALIAS, null) as? SecretKey
    }
}
