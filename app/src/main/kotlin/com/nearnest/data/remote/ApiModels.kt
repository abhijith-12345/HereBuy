// FILE GUIDE: Defines Kotlin serialization models for authentication payloads, users, and listing results.
// Change this file for the behavior described above; see workbook.md for its symbol map.

package com.nearnest.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Public fields returned for the authenticated account. */
@Serializable
data class ApiUser(
    val id: String,
    val username: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    val email: String? = null,
    @SerialName("display_name") val displayName: String,
    @SerialName("created_at") val createdAt: String
)

/** Successful authentication response and its access-token lifetime. */
@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
    val user: ApiUser
)

/** Credentials accepted by the login endpoint. */
@Serializable
data class PasswordLoginRequest(
    val identifier: String,
    val password: String
)

/** Account details submitted to begin email-verified signup. */
@Serializable
data class SignupStartRequest(
    val username: String,
    val email: String,
    val password: String
)

/** Challenge identifier and confirmation returned after signup code delivery. */
@Serializable
data class SignupOtpResponse(
    @SerialName("challenge_id") val challengeId: String,
    val message: String
)

/** Signup challenge and six-digit verification code. */
@Serializable
data class SignupVerifyRequest(
    @SerialName("challenge_id") val challengeId: String,
    val code: String
)

/** Signup challenge for requesting another verification code. */
@Serializable
data class SignupResendRequest(
    @SerialName("challenge_id") val challengeId: String
)

/** Listing details rendered in the seller's posted-items screen. */
@Serializable
data class ListingItem(
    val id: String,
    @SerialName("seller_id") val sellerId: String,
    val title: String,
    val description: String,
    val category: String,
    val price: Double,
    @SerialName("pickup_area") val pickupArea: String,
    val latitude: Double,
    val longitude: Double,
    val status: String,
    @SerialName("created_at") val createdAt: String
)

/** New live listing alerts and the backend timestamp for the next incremental check. */
@Serializable
data class ListingAlertsResponse(
    val listings: List<NearbyListing>,
    @SerialName("checked_at") val checkedAt: String
)

/** Device token and current radius/location sent to the authenticated push-registration API. */
@Serializable
data class PushDeviceRequest(
    val token: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("radius_meters") val radiusMeters: Int
)
