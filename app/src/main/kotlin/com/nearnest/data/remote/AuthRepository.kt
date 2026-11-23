package com.nearnest.data.remote

import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.auth

class AuthRepository(
    private val clientProvider: SupabaseClientProvider = SupabaseClientProvider
) {
    suspend fun hasSession(): Boolean {
        val auth = clientProvider.client.auth
        auth.awaitInitialization()
        return auth.currentSessionOrNull() != null
    }

    suspend fun signUp(email: String, password: String): Boolean {
        clientProvider.client.auth.signUpWith(
            provider = Email,
            redirectUrl = SupabaseClientProvider.AUTH_REDIRECT_URL
        ) {
            this.email = email.trim()
            this.password = password
        }
        return clientProvider.client.auth.currentSessionOrNull() != null
    }

    suspend fun signIn(email: String, password: String) {
        clientProvider.client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signOut() {
        clientProvider.client.auth.signOut()
    }
}
