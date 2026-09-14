package io.nativestarter.showcase.bluesky.core.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.nativestarter.showcase.bluesky.core.network.XrpcJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * Session storage: the session JSON is encrypted with AES-256-GCM under a key held in the Android Keystore,
 * and the ciphertext is kept in Preferences DataStore. This replaces androidx.security
 * EncryptedSharedPreferences, which is deprecated.
 *
 * - All work runs off the main thread.
 * - Data that cannot be decrypted or decoded (key invalidated, format change) is removed and treated as
 *   signed out. Other failures, including save failures, propagate to the caller.
 * - `android:allowBackup="false"`, so the ciphertext is not backed up without its key.
 */
class KeystoreSessionStore(
    context: Context,
    private val keyAlias: String = "session-key",
    private val dataStore: DataStore<Preferences> = context.applicationContext.sessionDataStore,
) : SessionStore {

    override suspend fun load(): Session? = withContext(Dispatchers.IO) {
        val blob = dataStore.data.first()[SESSION_KEY] ?: return@withContext null
        try {
            XrpcJson.decodeFromString(Session.serializer(), decrypt(blob))
        } catch (e: GeneralSecurityException) {
            clear()
            null
        } catch (e: SerializationException) {
            clear()
            null
        } catch (e: IllegalArgumentException) {
            clear()
            null
        }
    }

    override suspend fun save(session: Session) {
        val blob = withContext(Dispatchers.IO) { encrypt(XrpcJson.encodeToString(Session.serializer(), session)) }
        dataStore.edit { it[SESSION_KEY] = blob }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(SESSION_KEY) }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        require(bytes.size > IV_LENGTH) { "Session blob too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_LENGTH))
        return String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH), Charsets.UTF_8)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
        val SESSION_KEY = stringPreferencesKey("session.v1")
    }
}
