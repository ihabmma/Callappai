package com.example.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts and decrypts sensitive user-provided API keys using Android Keystore AES-GCM.
 */
object ApiKeyCipher {
    private const val TAG = "ApiKeyCipher"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "smart_call_responder_api_key_aes"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX_ENC = "enc_v1:"
    private const val GCM_TAG_BITS = 128

    private fun getOrCreateKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            Log.w(TAG, "Keystore unavailable in this environment, falling back to plain storage", e)
            null
        }
    }

    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return ""
        return try {
            val key = getOrCreateKey() ?: return plainText
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            "$PREFIX_ENC$ivB64:$dataB64"
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            plainText
        }
    }

    fun decrypt(stored: String): String {
        if (stored.isBlank()) return ""
        if (!stored.startsWith(PREFIX_ENC)) return stored
        return try {
            val payload = stored.removePrefix(PREFIX_ENC)
            val parts = payload.split(":", limit = 2)
            if (parts.size != 2) return ""
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val key = getOrCreateKey() ?: return ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            ""
        }
    }
}
