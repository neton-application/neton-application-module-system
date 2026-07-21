package controller.admin.user

import controller.admin.user.dto.ProfileUpdatePasswordRequest
import controller.admin.user.dto.UpdateUserRequest
import controller.admin.user.dto.UserVO
import logic.UserLogic
import neton.core.annotations.*
import neton.core.interfaces.Identity

@Controller("/system/user/profile")
class UserProfileController(
    private val userLogic: UserLogic
) {

    @Get("/get")
    suspend fun get(identity: Identity): UserVO {
        val userId = identity.id.toLong()
        return userLogic.getById(userId)
    }

    @Put("/update")
    suspend fun update(identity: Identity, @Body request: UpdateUserRequest) {
        val userId = identity.id.toLong()
        userLogic.update(request.copy(id = userId))
    }

    @Put("/update-password")
    suspend fun updatePassword(identity: Identity, @Body request: ProfileUpdatePasswordRequest) {
        val userId = identity.id.toLong()
        userLogic.updatePassword(userId, request.newPassword)
    }
}
