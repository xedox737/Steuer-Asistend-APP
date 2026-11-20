package com.example.data

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.InputStream
import java.io.OutputStream

/** Inner streaming layer. Envelope/header validation and publication are separate boundaries. */
internal object OfflineBackupStreamCipher {
    private fun primitive(key: ByteArray): AesGcmHkdfStreaming {
        require(key.size == 32) { "Ein Archivschlüssel muss genau 256 Bit besitzen." }
        return AesGcmHkdfStreaming(key, "HmacSha256", 32, 1024 * 1024, 0)
    }
    fun encrypt(key: ByteArray, authenticatedHeader: ByteArray, output: OutputStream): OutputStream =
        primitive(key).newEncryptingStream(output, authenticatedHeader)

    /** Callers must consume through EOF before accepting any decrypted archive. */
    fun decrypt(key: ByteArray, authenticatedHeader: ByteArray, input: InputStream): InputStream =
        primitive(key).newDecryptingStream(input, authenticatedHeader)
}
