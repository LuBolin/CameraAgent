package com.bolin.photohelper.visual

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DemoApiKeyStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    init {
        runCatching { appContext.getSharedPreferences(LEGACY_PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit() }
        runCatching { if (keyStore.containsAlias(LEGACY_KEY_ALIAS)) keyStore.deleteEntry(LEGACY_KEY_ALIAS) }
    }

    @Synchronized
    fun save(apiKey: CharArray) {
        var plaintext: ByteArray? = null
        var ciphertext: ByteArray? = null
        try {
            require(isValidApiKey(apiKey)) {
                "API key must contain 1..$MAX_API_KEY_CHARACTERS printable ASCII characters"
            }
            val encodedKey = ByteArray(apiKey.size) { apiKey[it].code.toByte() }
            plaintext = encodedKey
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            }
            val encryptedKey = cipher.doFinal(encodedKey)
            ciphertext = encryptedKey
            check(
                preferences.edit()
                    .putString(CIPHERTEXT_KEY, Base64.getEncoder().encodeToString(encryptedKey))
                    .putString(IV_KEY, Base64.getEncoder().encodeToString(cipher.iv))
                    .commit(),
            ) { "Could not store API key" }
        } finally {
            apiKey.fill('\u0000')
            plaintext?.fill(0)
            ciphertext?.fill(0)
        }
    }

    @Synchronized
    fun load(): CharArray? {
        val encodedCiphertext = preferences.getString(CIPHERTEXT_KEY, null)
        val encodedIv = preferences.getString(IV_KEY, null)
        if (encodedCiphertext == null && encodedIv == null) return null
        if (encodedCiphertext == null || encodedIv == null) {
            clearCorruptEntry()
            return null
        }

        var plaintext: ByteArray? = null
        return try {
            val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (key == null) {
                clearCorruptEntry()
                return null
            }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    key,
                    GCMParameterSpec(128, Base64.getDecoder().decode(encodedIv)),
                )
            }
            val decryptedKey = cipher.doFinal(Base64.getDecoder().decode(encodedCiphertext))
            plaintext = decryptedKey
            if (decryptedKey.any { (it.toInt() and 0xff) !in 0x21..0x7e }) {
                clearCorruptEntry()
                null
            } else {
                CharArray(decryptedKey.size) { (decryptedKey[it].toInt() and 0xff).toChar() }
            }
        } catch (_: GeneralSecurityException) {
            clearCorruptEntry()
            null
        } catch (_: IllegalArgumentException) {
            clearCorruptEntry()
            null
        } finally {
            plaintext?.fill(0)
        }
    }

    @Synchronized
    fun hasKey(): Boolean =
        preferences.contains(CIPHERTEXT_KEY) &&
            preferences.contains(IV_KEY) &&
            keyStore.containsAlias(KEY_ALIAS)

    @Synchronized
    fun clear() {
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        check(preferences.edit().remove(CIPHERTEXT_KEY).remove(IV_KEY).commit()) {
            "Could not clear stored API key"
        }
    }

    private fun getOrCreateKey(): SecretKey =
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE,
        ).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }

    private fun clearCorruptEntry() {
        runCatching { clear() }
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFERENCES_NAME = "photo_helper_bailian_demo_api_key_v1"
        const val CIPHERTEXT_KEY = "ciphertext"
        const val IV_KEY = "iv"
        const val KEY_ALIAS = "photo_helper_bailian_demo_api_key_v1"
        const val LEGACY_PREFERENCES_NAME = "photo_helper_demo_api_key"
        const val LEGACY_KEY_ALIAS = "photo_helper_zai_demo_api_key_v1"
    }
}
