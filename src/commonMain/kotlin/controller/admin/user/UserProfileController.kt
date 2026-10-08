package controller.admin.user

import controller.admin.user.dto.ProfileUpdatePasswordRequest
import controller.admin.user.dto.ProfileUpdateRequest
import controller.admin.user.dto.UserVO
import logic.UserLogic
import neton.core.annotations.*
import neton.core.interfaces.Identity

/**
 * 当前登录者自己的资料与密码。不挂 @Permission：任何后台账号都能管自己，
 * 所以这里绝不能接受能越权的字段（见 [ProfileUpdateRequest]）。
 */
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
    suspend fun update(identity: Identity, @Body request: ProfileUpdateRequest) {
        userLogic.updateProfile(identity.id.toLong(), request)
    }

    @Put("/update-password")
    suspend fun updatePassword(identity: Identity, @Body request: ProfileUpdatePasswordRequest) {
        userLogic.changeOwnPassword(identity.id.toLong(), request.oldPassword, request.newPassword)
    }
}
