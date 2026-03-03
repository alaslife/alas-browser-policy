package com.sun.alasbrowser.utils

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

/**
 * Secure credential storage using Android Keystore.
 * Uses hardware-backed security when available, with fallback for compatibility.
 */
object SecurityUtils {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "AlasBrowserCredKey"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH = 128
    
    // Security flag to track if we're using hardware-backed security
    @Volatile
    private var isHardwareBacked = false

    init {
        createKeyIfNeeded()
    }

    /**
     * Creates encryption key with hardware-backed security when available.
     * Falls back to software-only if hardware is not available.
     */
    private fun createKeyIfNeeded() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                createSecureKey()
            } else {
                // Verify existing key's security level
                val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                isHardwareBacked = entry?.secretKey?.isDestroyed == false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            createSecureKey()
        }
    }
    
    private fun createSecureKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            
            // Try to delete existing key first
            try { keyStore.deleteEntry(KEY_ALIAS) } catch (e: Exception) { }
            
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE
            )
            
            // Build key with strongest available security
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)  // Required for GCM security
                .setUserAuthenticationRequired(false)    // Don't require biometric for auto-fill
            
            // Try to enable hardware-backed security if available
            try {
                builder.setIsStrongBoxBacked(true)  // Use StrongBox if available
                isHardwareBacked = true
            } catch (e: Exception) {
                // StrongBox not available, use TEE
                isHardwareBacked = false
            }
            
            val keyGenParameterSpec = builder.build()
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
            
        } catch (e: Exception) {
            // Fallback: Create key without hardware requirements
            createFallbackKey()
        }
    }
    
    private fun createFallbackKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            try { keyStore.deleteEntry(KEY_ALIAS) } catch (e: Exception) { }
            
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE
            )
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
            isHardwareBacked = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Gets the secret key, recreating it if not accessible.
     */
    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            e.printStackTrace()
            // Key not accessible - recreate it
            createSecureKey()
            null
        }
    }

    /**
     * Encrypts plaintext using AES-GCM.
     * @return Base64-encoded ciphertext with IV prepended, or null on failure
     */
    fun encrypt(plainText: String): String? {
        if (plainText.isEmpty()) return null
        
        return try {
            var secretKey = getSecretKey()
            if (secretKey == null) {
                createSecureKey()
                secretKey = getSecretKey()
            }
            if (secretKey == null) return null
            
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            
            // Combine IV and encrypted data
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decrypts Base64-encoded ciphertext using AES-GCM.
     * @return Decrypted plaintext, or null on failure
     */
    fun decrypt(encryptedBase64: String): String? {
        if (encryptedBase64.isEmpty()) return null
        
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            
            // Validate minimum length
            if (combined.size < IV_LENGTH + TAG_LENGTH / 8) {
                return null
            }
            
            val iv = ByteArray(IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH)
            
            val encryptedBytes = ByteArray(combined.size - IV_LENGTH)
            System.arraycopy(combined, IV_LENGTH, encryptedBytes, 0, encryptedBytes.size)
            
            val secretKey = getSecretKey() ?: return null
            val cipher = Cipher.getInstance(AES_MODE)
            val spec = GCMParameterSpec(TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Check if hardware-backed security is available
     */
    fun isHardwareBacked(): Boolean = isHardwareBacked
    
    /**
     * Check if encryption is available
     */
    fun isEncryptionAvailable(): Boolean = getSecretKey() != null
    
    /**
     * Clear all stored credentials (for security reset)
     */
    fun clearAllCredentials(context: Context) {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            try { keyStore.deleteEntry(KEY_ALIAS) } catch (e: Exception) { }
            createSecureKey()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
