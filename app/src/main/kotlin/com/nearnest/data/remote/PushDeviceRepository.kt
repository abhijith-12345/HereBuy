// FILE GUIDE: Registers the current FCM device and nearby alert preferences with the backend.
package com.nearnest.data.remote

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.put
import io.ktor.client.request.setBody

/** Sends the signed-in user's push token and latest alert coordinates to HereBuy API. */
class PushDeviceRepository {
    suspend fun register(
        accessToken: String,
        pushToken: String,
        latitude: Double,
        longitude: Double,
        radiusKm: Int
    ) {
        val response = ApiClientProvider.client.put("${ApiClientProvider.baseUrl}/notifications/device") {
            bearerAuth(accessToken)
            setBody(PushDeviceRequest(pushToken, latitude, longitude, radiusKm * 1_000))
        }
        response.requireSuccess()
    }
}
