package com.nearnest.data.remote

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get

class NearbyListingsRepository {
    suspend fun getNearby(
        accessToken: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ): List<NearbyListing> {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90." }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180." }

        val url = "${ApiClientProvider.baseUrl}/listings/nearby" +
            "?latitude=$latitude&longitude=$longitude&radius_meters=${radiusMeters.coerceIn(1_000, 20_000)}"
        val response = ApiClientProvider.client.get(url) {
            bearerAuth(accessToken)
        }
        response.requireSuccess()
        return response.body()
    }
}
