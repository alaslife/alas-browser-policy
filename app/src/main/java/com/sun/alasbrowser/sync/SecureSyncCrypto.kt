package com.sun.alasbrowser.sync

import android.util.Base64
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class EncryptedSyncEnvelope(
    val version: Int = 1,
    val salt: String,
    val iv: String,
    val ciphertext: String,
    val createdAt: Long = System.currentTimeMillis()
)

object SecureSyncCrypto {
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_SIZE_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12

    private val secureRandom = SecureRandom()

    fun encrypt(plainText: String, passphrase: String): EncryptedSyncEnvelope {
        val salt = ByteArray(SALT_BYTES)
        val iv = ByteArray(IV_BYTES)
        secureRandom.nextBytes(salt)
        secureRandom.nextBytes(iv)

        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        return EncryptedSyncEnvelope(
            salt = b64(salt),
            iv = b64(iv),
            ciphertext = b64(encrypted)
        )
    }

    fun decrypt(envelope: EncryptedSyncEnvelope, passphrase: String): String {
        val salt = b64Decode(envelope.salt)
        val iv = b64Decode(envelope.iv)
        val cipherBytes = b64Decode(envelope.ciphertext)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_SIZE_BITS)
        val key = factory.generateSecret(spec).encoded
        return SecretKeySpec(key, "AES")
    }

    private fun b64(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun b64Decode(data: String): ByteArray {
        return Base64.decode(data, Base64.NO_WRAP)
    }
}
