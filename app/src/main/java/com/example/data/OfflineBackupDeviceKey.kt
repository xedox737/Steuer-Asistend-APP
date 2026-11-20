package com.example.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AndroidKeyStore boundary only. Protected records can be persisted; raw MKs cannot. */
internal object OfflineBackupDeviceKey {
    internal const val ALIAS = "immopilot_backup_root_v2"
    private const val PROVIDER = "AndroidKeyStore"
    private val lock = Any()
    data class Protected(val recovery: OfflineBackupKeyEnvelope.Recovery, val iv: ByteArray, val ciphertext: ByteArray)

    /** Explicit setup only; opening a saved generation must never invoke this method. */
    fun initialize(): Unit = synchronized(lock) {
        val store = store()
        if (store.containsAlias(ALIAS)) {
            key(store) // Reject incompatible existing entries instead of replacing them.
        } else {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
                init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false).build())
            }.generateKey()
        }
        Unit
    }
    fun protect(generation: OfflineBackupKeyEnvelope.Generation): Protected = synchronized(lock) {
        val master = generation.masterCopy()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            // The Keystore provider chooses a fresh nonce; no caller-supplied encryption IV.
            cipher.init(Cipher.ENCRYPT_MODE, key(store()))
            cipher.updateAAD(context(generation.recovery))
            Protected(generation.recovery, cipher.iv.copyOf(), cipher.doFinal(master))
        } finally { master.fill(0) }
    }
    fun open(record: Protected): OfflineBackupKeyEnvelope.Generation = synchronized(lock) {
        require(record.iv.size == 12 && record.ciphertext.size == 48) { "Ungültige geschützte Schlüsselgeneration." }
        val aad = context(record.recovery)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(store()), GCMParameterSpec(128, record.iv))
            cipher.updateAAD(aad)
            val master = cipher.doFinal(record.ciphertext)
            try { OfflineBackupKeyEnvelope.Generation(record.recovery, master) }
            finally { master.fill(0) }
        } catch (_: GeneralSecurityException) {
            throw OfflineBackupValidationException("Die geschützte Schlüsselgeneration kann nicht geöffnet werden. Bitte Recovery-Zugang prüfen.")
        }
    }
    private fun store() = KeyStore.getInstance(PROVIDER).apply { load(null) }
    private fun key(store: KeyStore): SecretKey = store.getKey(ALIAS, null) as? SecretKey
        ?: throw OfflineBackupValidationException("Der Geräteschlüssel fehlt. Es wurde kein Ersatzschlüssel erzeugt.")

    private fun context(recovery: OfflineBackupKeyEnvelope.Recovery): ByteArray {
        val dummy = OfflineBackupKeyEnvelope.WrappedDataKey(recovery.generationId,
            "00000000-0000-0000-0000-000000000000", ByteArray(12), ByteArray(48))
        return "ImmoPilotDeviceMasterV2".toByteArray(Charsets.US_ASCII) + OfflineBackupArchiveHeader.encode(recovery, dummy)
    }
}
