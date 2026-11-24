// FILE GUIDE: Fetches available listings inside validated visible map bounds.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.data.remote

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get

/** Provides the viewport-listings API operation used by the map. */

class NearbyListingsRepository {
    /** Fetches every available listing inside the visible map bounds, nearest viewport center first. */
    suspend fun getInViewport(
        accessToken: String,
        west: Double,
        south: Double,
        east: Double,
        north: Double,
        centerLatitude: Double,
        centerLongitude: Double
    ): List<NearbyListing> {
        require(west in -180.0..180.0 && east in -180.0..180.0) { "Viewport longitude is out of range." }
        require(south in -90.0..90.0 && north in -90.0..90.0 && south <= north) {
            "Viewport latitude is invalid."
        }
        require(centerLatitude in -90.0..90.0) { "Center latitude must be between -90 and 90." }
        require(centerLongitude in -180.0..180.0) { "Center longitude must be between -180 and 180." }

        val url = "${ApiClientProvider.baseUrl}/listings/viewport" +
            "?west=$west&south=$south&east=$east&north=$north" +
            "&center_latitude=$centerLatitude&center_longitude=$centerLongitude"
        val response = ApiClientProvider.client.get(url) {
            bearerAuth(accessToken)
        }
        response.requireSuccess()
        return response.body()
    }
}
