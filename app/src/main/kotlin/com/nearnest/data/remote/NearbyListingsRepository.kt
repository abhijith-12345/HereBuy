package com.nearnest.data.remote

import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.postgrest

class NearbyListingsRepository(
    private val clientProvider: SupabaseClientProvider = SupabaseClientProvider
) {
    suspend fun getNearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ): List<NearbyListing> {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90." }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180." }

        val response = clientProvider.client.postgrest.rpc(
            function = "get_nearby_listings",
            parameters = NearbyListingsRequest(
                centerLatitude = latitude,
                centerLongitude = longitude,
                radiusMeters = radiusMeters.coerceIn(1_000, 20_000)
            )
        )
        return response.decodeList<NearbyListing>()
    }
}
