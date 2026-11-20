package com.example.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.util.UUID

/** Bounded binary envelope. Parsing never derives a key or accepts unauthenticated archive data. */
internal object OfflineBackupArchiveHeader {
    data class Parsed(val recovery: OfflineBackupKeyEnvelope.Recovery,
        val dataKey: OfflineBackupKeyEnvelope.WrappedDataKey, val authenticatedBytes: ByteArray)
    private val magic = "IMMOFULL".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 2
    private const val MAX_HEADER = 64 * 1024
    private const val MIN_PAYLOAD = 174

    fun encode(recovery: OfflineBackupKeyEnvelope.Recovery, dataKey: OfflineBackupKeyEnvelope.WrappedDataKey): ByteArray {
        validate(recovery, dataKey)
        val payload = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { output ->
            output.writeByte(1) // AES256-GCM-HKDF streaming, SHA256, 1 MiB, offset 0.
            writeId(output, recovery.generationId); writeId(output, dataKey.archiveId)
            output.writeInt(recovery.iterations); output.writeByte(recovery.salt.size); output.write(recovery.salt)
            output.write(recovery.iv); output.write(recovery.wrappedMaster)
            output.write(dataKey.iv); output.write(dataKey.ciphertext)
        } }.toByteArray()
        return ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { output ->
            output.write(magic); output.writeInt(VERSION); output.writeInt(payload.size); output.write(payload)
        } }.toByteArray()
    }

    fun read(input: InputStream): Parsed {
        val source = DataInputStream(input)
        val prefix = ByteArray(magic.size).also(source::readFully)
        if (!prefix.contentEquals(magic)) invalid("Keine unterstützte verschlüsselte ImmoPilot-Sicherung.")
        val version = source.readInt()
        if (version > VERSION) invalid("Neuere Sicherung: Bitte die App aktualisieren.")
        if (version != VERSION) invalid("Nicht unterstützte verschlüsselte Sicherungsversion.")
        val length = source.readInt()
        if (length !in MIN_PAYLOAD..(MAX_HEADER - 16)) invalid("Ungültige Größe des Sicherungsheaders.")
        val payload = ByteArray(length).also(source::readFully)
        val decoded = DataInputStream(ByteArrayInputStream(payload))
        if (decoded.readUnsignedByte() != 1) invalid("Nicht unterstütztes Verschlüsselungsverfahren.")
        val generation = readId(decoded)
        val archive = readId(decoded)
        val iterations = decoded.readInt()
        if (iterations !in 600_000..2_000_000) invalid("Nicht unterstützter Recovery-KDF-Aufwand.")
        val saltLength = decoded.readUnsignedByte()
        if (saltLength !in 16..32) invalid("Ungültige Recovery-Salt-Länge.")
        fun bytes(size: Int) = ByteArray(size).also(decoded::readFully)
        val recovery = OfflineBackupKeyEnvelope.Recovery(generation, bytes(saltLength), iterations, bytes(12), bytes(48))
        val key = OfflineBackupKeyEnvelope.WrappedDataKey(generation, archive, bytes(12), bytes(48))
        if (decoded.available() != 0) invalid("Unbekannte zusätzliche Sicherungsheaderdaten.")
        validate(recovery, key)
        val raw = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { output ->
            output.write(prefix); output.writeInt(version); output.writeInt(length); output.write(payload)
        } }.toByteArray()
        return Parsed(recovery, key, raw)
    }
    private fun validate(recovery: OfflineBackupKeyEnvelope.Recovery, key: OfflineBackupKeyEnvelope.WrappedDataKey) {
        require(UUID.fromString(recovery.generationId).toString() == recovery.generationId)
        require(UUID.fromString(key.archiveId).toString() == key.archiveId)
        require(key.generationId == recovery.generationId)
        require(recovery.iterations in 600_000..2_000_000 && recovery.salt.size in 16..32)
        require(recovery.iv.size == 12 && key.iv.size == 12 && recovery.wrappedMaster.size == 48 && key.ciphertext.size == 48)
    }
    private fun writeId(output: DataOutputStream, id: String) {
        val uuid = UUID.fromString(id); output.writeLong(uuid.mostSignificantBits); output.writeLong(uuid.leastSignificantBits)
    }
    private fun readId(input: DataInputStream) = UUID(input.readLong(), input.readLong()).toString()
    private fun invalid(message: String): Nothing = throw OfflineBackupValidationException(message)
}
