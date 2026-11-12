package com.example.data

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Small, independently encrypted section: plaintext exists only in caller-owned memory. */
internal object OfflineBackupCredentialSection {
    enum class Provider(val id: Int) { OPENAI(1), GEMINI(2), GOOGLE_ROUTES(3) }
    enum class Trigger { MANUAL, SCHEDULED }
    data class Consent(val manual: Boolean = false, val scheduled: Boolean = false) {
        fun allows(trigger: Trigger) = when (trigger) { Trigger.MANUAL -> manual; Trigger.SCHEDULED -> scheduled }
    }
    class Opened internal constructor(val values: Map<Provider, CharArray>) : AutoCloseable {
        override fun close() { values.values.forEach { it.fill('\u0000') } }
    }
    private val magic = "IMMCRED2".toByteArray(Charsets.US_ASCII)
    private const val MAX_PLAINTEXT = 32 * 1024
    private const val MAX_SECTION = 64 * 1024 + MAX_PLAINTEXT + 64
    private class WipingBuffer : ByteArrayOutputStream() { fun wipe() { buf.fill(0); reset() } }

    /** Consumes every input buffer even when excluded, invalid, or encryption fails. */
    fun seal(generation: OfflineBackupKeyEnvelope.Generation, values: Map<Provider, CharArray>,
        consent: Consent = Consent(), trigger: Trigger = Trigger.MANUAL): ByteArray? {
        val buffer = WipingBuffer()
        var plaintext: ByteArray? = null
        val key = ByteArray(32)
        try {
            if (!consent.allows(trigger)) return null
            require(values.size <= 3 && values.values.all { it.size in 1..4096 })
            DataOutputStream(buffer).apply {
                writeByte(values.size)
                values.toSortedMap(compareBy { it.id }).forEach { (provider, value) ->
                    writeByte(provider.id); writeShort(value.size); value.forEach { writeChar(it.code) }
                }
                flush()
            }
            plaintext = buffer.toByteArray()
            check(plaintext.size <= MAX_PLAINTEXT)
            SecureRandom().nextBytes(key)
            val wrapped = OfflineBackupKeyEnvelope.wrapDataKey(generation, UUID.randomUUID().toString(), key)
            val header = OfflineBackupArchiveHeader.encode(generation.recovery, wrapped)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            cipher.updateAAD(magic + header)
            return magic + header + cipher.iv + cipher.doFinal(plaintext)
        } finally {
            plaintext?.fill(0); buffer.wipe(); key.fill(0); values.values.forEach { it.fill('\u0000') }
        }
    }

    /** No file or preference writes. Caller must close the returned secrets after in-memory use. */
    fun open(generation: OfflineBackupKeyEnvelope.Generation, section: ByteArray): Opened {
        var plaintext: ByteArray? = null
        var key: ByteArray? = null
        val values = linkedMapOf<Provider, CharArray>()
        var complete = false
        try {
            require(section.size in 1..MAX_SECTION)
            DataInputStream(section.inputStream()).use { input ->
                require(ByteArray(magic.size).also(input::readFully).contentEquals(magic))
                val header = OfflineBackupArchiveHeader.read(input)
                key = OfflineBackupKeyEnvelope.unwrapDataKey(generation, header.dataKey)
                val iv = ByteArray(12).also(input::readFully)
                val encrypted = input.readBytes()
                require(encrypted.size in 16..(MAX_PLAINTEXT + 16))
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(requireNotNull(key), "AES"), GCMParameterSpec(128, iv))
                cipher.updateAAD(magic + header.authenticatedBytes)
                plaintext = cipher.doFinal(encrypted)
            }
            DataInputStream(requireNotNull(plaintext).inputStream()).use { input ->
                val count = input.readUnsignedByte(); require(count <= 3)
                repeat(count) {
                    val id = input.readUnsignedByte()
                    val provider = Provider.entries.singleOrNull { it.id == id }
                        ?: throw IllegalArgumentException()
                    require(provider !in values)
                    val size = input.readUnsignedShort(); require(size in 1..4096)
                    val value = CharArray(size); values[provider] = value
                    for (index in value.indices) value[index] = input.readChar()
                }
                require(input.read() == -1)
            }
            complete = true
            return Opened(values)
        } catch (_: GeneralSecurityException) { invalid() }
        catch (_: IOException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        finally {
            plaintext?.fill(0); key?.fill(0)
            if (!complete) values.values.forEach { it.fill('\u0000') }
        }
    }
    private fun invalid(): Nothing = throw OfflineBackupValidationException("Der verschlüsselte API-Schlüsselabschnitt ist ungültig. Es wurden keine Schlüssel übernommen.")
}
