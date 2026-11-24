package com.nearnest.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiUser(
    val id: String,
    val username: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    val email: String? = null,
    @SerialName("display_name") val displayName: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
    val user: ApiUser
)

@Serializable
data class PasswordLoginRequest(
    val identifier: String,
    val password: String
)

@Serializable
data class SignupStartRequest(
    val username: String,
    val email: String,
    val password: String
)

@Serializable
data class SignupOtpResponse(
    @SerialName("challenge_id") val challengeId: String,
    val message: String
)

@Serializable
data class SignupVerifyRequest(
    @SerialName("challenge_id") val challengeId: String,
    val code: String
)

@Serializable
data class SignupResendRequest(
    @SerialName("challenge_id") val challengeId: String
)
