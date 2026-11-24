// FILE GUIDE: Fetches listings owned by the signed-in seller.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.data.remote

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get

/** Provides the seller-owned-listings API operation. */

class MyListingsRepository {
    /** Fetches listings belonging to the authenticated seller. */
    suspend fun getMine(accessToken: String): List<ListingItem> {
        val response = ApiClientProvider.client.get("${ApiClientProvider.baseUrl}/listings/mine") {
            bearerAuth(accessToken)
        }
        response.requireSuccess()
        return response.body()
    }
}
