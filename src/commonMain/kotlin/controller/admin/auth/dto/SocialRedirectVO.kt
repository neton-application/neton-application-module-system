package controller.admin.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class SocialRedirectVO(
    val url: String
)
