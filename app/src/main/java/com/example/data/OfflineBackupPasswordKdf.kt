package com.example.data

import org.bouncycastle.crypto.PBEParametersGenerator
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter

/** Library PBKDF2 for API 24/25 as well; never falls back to SHA-1. Consumes password. */
internal object OfflineBackupPasswordKdf {
    fun derive(password: CharArray, salt: ByteArray, iterations: Int = 600_000): ByteArray {
        var encoded: ByteArray? = null
        try {
            require(password.size in 16..1024) { "Das Recovery-Passwort muss 16 bis 1024 Zeichen enthalten." }
            require(salt.size in 16..32) { "Ungültige Recovery-Salt-Länge." }
            require(iterations in 600_000..2_000_000) { "Nicht unterstützter Recovery-KDF-Aufwand." }
            val bytes = PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(password)
            encoded = bytes
            val generator = PKCS5S2ParametersGenerator(SHA256Digest())
            generator.init(bytes, salt, iterations)
            return (generator.generateDerivedParameters(256) as KeyParameter).key.copyOf()
        } finally {
            encoded?.fill(0)
            password.fill('\u0000')
        }
    }
}
