package controller.admin.auth.dto

import kotlinx.serialization.Serializable

@Serializable
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
data class LoginResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val userId: Long? = null,
    val expiresTime: Long? = null,
    val username: String? = null,
    val nickname: String? = null,
    @kotlinx.serialization.EncodeDefault val status: String = "authenticated",
    val challengeToken: String? = null,
    val challengeExpiresAt: Long? = null,
)
