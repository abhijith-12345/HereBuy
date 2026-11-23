package com.nearnest.data.remote

import com.nearnest.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.SupabaseClient

/** Creates the app-wide Supabase client from ignored local.properties values. */
object SupabaseClientProvider {
    const val AUTH_REDIRECT_SCHEME = "com.nearnest"
    const val AUTH_REDIRECT_HOST = "auth-callback"
    const val AUTH_REDIRECT_URL = "$AUTH_REDIRECT_SCHEME://$AUTH_REDIRECT_HOST"

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    val client: SupabaseClient by lazy {
        check(isConfigured) {
            "Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY to local.properties."
        }

        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = AUTH_REDIRECT_SCHEME
                host = AUTH_REDIRECT_HOST
            }
            install(Postgrest)
        }
    }
}
