// FILE GUIDE: Configures the shared Ktor HTTP client and reads the backend API base URL.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.data.remote

import com.nearnest.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Shared HTTP client for the HereBuy FastAPI service. */
object ApiClientProvider {
    val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/')

    val isConfigured: Boolean
        get() = baseUrl.startsWith("https://") || baseUrl.startsWith("http://")

    val client: HttpClient by lazy {
        HttpClient(Android) {
            expectSuccess = false
            install(DefaultRequest) {
                header("ngrok-skip-browser-warning", "true")
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }
}
