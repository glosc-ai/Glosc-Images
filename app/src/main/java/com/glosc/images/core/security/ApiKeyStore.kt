package com.glosc.images.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.glosc.images.core.common.AppException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ApiKeyStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("secure_api_keys", Context.MODE_PRIVATE)

    fun save(alias: String, apiKey: String) {
        if (apiKey.isBlank()) return
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecret(alias))
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("$alias.iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("$alias.value", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun read(alias: String): String? {
        val iv = prefs.getString("$alias.iv", null) ?: return null
        val value = prefs.getString("$alias.value", null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getExistingSecret(alias),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(value, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun has(alias: String): Boolean = prefs.contains("$alias.value")

    private fun getExistingSecret(alias: String): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getEntry(keyAlias(alias), null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: throw AppException("API Key 加密密钥不存在")
    }

    private fun getOrCreateSecret(alias: String): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(keyAlias(alias), null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            keyAlias(alias),
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun keyAlias(alias: String) = "glosc.images.$alias"

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
