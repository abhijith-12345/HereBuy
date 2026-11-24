package com.nearnest.data.remote

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class CreateListingRepository {
    suspend fun createListing(
        accessToken: String,
        title: String,
        description: String,
        category: String,
        price: Double,
        pickupArea: String,
        latitude: Double,
        longitude: Double
    ) {
        val response = ApiClientProvider.client.post("${ApiClientProvider.baseUrl}/listings") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                NewListing(
                    title = title,
                    description = description,
                    category = category,
                    price = price,
                    pickupArea = pickupArea,
                    latitude = latitude,
                    longitude = longitude
                )
            )
        }
        response.requireSuccess()
    }
}

@Serializable
private data class NewListing(
    val title: String,
    val description: String,
    val category: String,
    val price: Double,
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList(),
    @SerialName("pickup_area") val pickupArea: String,
    val latitude: Double,
    val longitude: Double
)
