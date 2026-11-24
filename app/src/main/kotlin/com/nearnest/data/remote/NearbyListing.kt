package com.nearnest.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NearbyListing(
    val id: String,
    @SerialName("seller_id") val sellerId: String,
    val title: String,
    val description: String,
    val category: String,
    val price: Double,
    @SerialName("photo_urls") val photoUrls: List<String> = emptyList(),
    @SerialName("pickup_area") val pickupArea: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("distance_meters") val distanceMeters: Double
)
