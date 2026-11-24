package com.nearnest.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AuthRepository(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun hasSession(): Boolean = getAccessToken() != null

    fun getAccessToken(): String? {
        val encryptedToken = preferences.getString(KEY_ACCESS_TOKEN, null) ?: return null
        return runCatching { decrypt(encryptedToken) }.getOrNull()
            ?: run {
                preferences.edit().remove(KEY_ACCESS_TOKEN).apply()
                null
            }
    }

    suspend fun fetchCurrentUser(): ApiUser {
        val token = getAccessToken() ?: throw ApiException("Please sign in again.", 401)
        val response = ApiClientProvider.client.get("${ApiClientProvider.baseUrl}/auth/me") {
            bearerAuth(token)
        }
        response.requireSuccess()
        return response.body()
    }

    suspend fun signIn(identifier: String, password: String): ApiUser {
        val response = ApiClientProvider.client.post("${ApiClientProvider.baseUrl}/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(PasswordLoginRequest(identifier.trim(), password))
        }
        response.requireSuccess()
        return response.body<AuthResponse>().also { saveSession(it.accessToken) }.user
    }

    suspend fun beginSignup(username: String, email: String, password: String): SignupOtpResponse {
        val response = ApiClientProvider.client.post("${ApiClientProvider.baseUrl}/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(SignupStartRequest(username.trim(), email.trim(), password))
        }
        response.requireSuccess()
        return response.body()
    }

    suspend fun verifySignup(challengeId: String, code: String): ApiUser {
        val response = ApiClientProvider.client.post("${ApiClientProvider.baseUrl}/auth/signup/verify") {
            contentType(ContentType.Application.Json)
            setBody(SignupVerifyRequest(challengeId, code))
        }
        response.requireSuccess()
        return response.body<AuthResponse>().also { saveSession(it.accessToken) }.user
    }

    suspend fun resendSignupCode(challengeId: String): SignupOtpResponse {
        val response = ApiClientProvider.client.post("${ApiClientProvider.baseUrl}/auth/signup/resend") {
            contentType(ContentType.Application.Json)
            setBody(SignupResendRequest(challengeId))
        }
        response.requireSuccess()
        return response.body()
    }

    suspend fun signOut() {
        preferences.edit().clear().apply()
    }

    private fun saveSession(accessToken: String) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, encrypt(accessToken))
            .apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.size > GCM_IV_BYTES) { "Invalid encrypted session token." }
        val iv = payload.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = payload.copyOfRange(GCM_IV_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "herebuy_api_session"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_ALIAS = "herebuy_api_session_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
