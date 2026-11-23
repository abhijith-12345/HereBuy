package com.nearnest.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NearbyListingsRequest(
    @SerialName("center_latitude") val centerLatitude: Double,
    @SerialName("center_longitude") val centerLongitude: Double,
    @SerialName("radius_meters") val radiusMeters: Int
)

@Serializable
data class NearbyListing(
    val id: String,
    @SerialName("seller_id") val sellerId: String,
    val title: String,
    val description: String,
    val category: String,
    val price: Double,
    @SerialName("photo_paths") val photoPaths: List<String>,
    @SerialName("pickup_area") val pickupArea: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("distance_meters") val distanceMeters: Double
)
