package com.example.data

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** In-memory MK/DEK envelopes. Never persists an unlocked key or password. */
internal object OfflineBackupKeyEnvelope {
    data class Recovery(val generationId: String, val salt: ByteArray, val iterations: Int,
        val iv: ByteArray, val wrappedMaster: ByteArray)
    data class WrappedDataKey(val generationId: String, val archiveId: String, val iv: ByteArray, val ciphertext: ByteArray)
    class Generation internal constructor(val recovery: Recovery, key: ByteArray) : AutoCloseable {
        private val master = key.copyOf().also { require(it.size == 32) }
        private var closed = false
        internal fun masterCopy(): ByteArray { check(!closed) { "Die Schlüsselgeneration ist geschlossen." }; return master.copyOf() }
        override fun close() { master.fill(0); closed = true }
    }
    private val random = SecureRandom()
    private fun bytes(size: Int) = ByteArray(size).also(random::nextBytes)
    private fun identifier(value: String) { require(UUID.fromString(value).toString() == value) { "Ungültige Sicherungskennung." } }
    private fun aad(role: String, generation: String, archive: String = "", iterations: Int = 0, salt: ByteArray = byteArrayOf()): ByteArray =
        ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { output ->
            output.writeUTF("ImmoPilotBackupV2"); output.writeUTF(role); output.writeUTF(generation)
            output.writeUTF(archive); output.writeInt(iterations); output.writeInt(salt.size); output.write(salt)
        } }.toByteArray()
    private fun crypt(mode: Int, key: ByteArray, iv: ByteArray, context: ByteArray, input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(context)
        return cipher.doFinal(input)
    }
    fun create(password: CharArray): Generation {
        val master = bytes(32)
        var kek: ByteArray? = null
        try {
            val id = UUID.randomUUID().toString()
            val salt = bytes(16)
            val iv = bytes(12)
            kek = OfflineBackupPasswordKdf.derive(password, salt)
            val wrapped = crypt(Cipher.ENCRYPT_MODE, kek, iv, aad("MK", id, iterations = 600_000, salt = salt), master)
            return Generation(Recovery(id, salt, 600_000, iv, wrapped), master)
        } finally { kek?.fill(0); master.fill(0); password.fill('\u0000') }
    }
    fun recover(record: Recovery, password: CharArray): Generation {
        var kek: ByteArray? = null
        var master: ByteArray? = null
        try {
            identifier(record.generationId)
            require(record.salt.size in 16..32 && record.iv.size == 12 && record.wrappedMaster.size == 48)
            require(record.iterations in 600_000..2_000_000)
            kek = OfflineBackupPasswordKdf.derive(password, record.salt, record.iterations)
            master = crypt(Cipher.DECRYPT_MODE, kek, record.iv,
                aad("MK", record.generationId, iterations = record.iterations, salt = record.salt), record.wrappedMaster)
            return Generation(record, master)
        } finally { kek?.fill(0); master?.fill(0); password.fill('\u0000') }
    }
    fun wrapDataKey(generation: Generation, archiveId: String, dataKey: ByteArray): WrappedDataKey {
        identifier(archiveId)
        require(dataKey.size == 32)
        val master = generation.masterCopy()
        try {
            val iv = bytes(12)
            val id = generation.recovery.generationId
            return WrappedDataKey(id, archiveId, iv, crypt(Cipher.ENCRYPT_MODE, master, iv, aad("DEK", id, archiveId), dataKey))
        } finally { master.fill(0) }
    }
    fun unwrapDataKey(generation: Generation, record: WrappedDataKey): ByteArray {
        identifier(record.archiveId)
        require(record.generationId == generation.recovery.generationId && record.iv.size == 12 && record.ciphertext.size == 48)
        val master = generation.masterCopy()
        try { return crypt(Cipher.DECRYPT_MODE, master, record.iv, aad("DEK", record.generationId, record.archiveId), record.ciphertext) }
        finally { master.fill(0) }
    }
}
