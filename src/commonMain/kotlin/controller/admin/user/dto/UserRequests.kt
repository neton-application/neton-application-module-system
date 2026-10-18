package controller.admin.user.dto

import logic.SystemErrorCodes
import kotlinx.serialization.Serializable
import neton.validation.annotations.Email
import neton.validation.annotations.Max
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Pattern
import neton.validation.annotations.Size

@Serializable
data class CreateUserRequest(
    @property:NotBlank
    @property:Size(min = 3, max = 64)
    val username: String,

    @property:NotBlank
    @property:Size(min = 8, max = 128)
    val password: String,

    @property:NotBlank
    @property:Size(min = 2, max = 32)
    val nickname: String,

    @property:Min(1)
    val deptId: Long? = null,

    val postIds: List<Long>? = null,

    @property:Email
    val email: String? = null,

    @property:Pattern(regex = "^1\\d{10}$", message = SystemErrorCodes.MOBILE_INVALID)
    val mobile: String? = null,

    @property:Min(0)
    @property:Max(2)
    val gender: Int = 0,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1,

    @property:Size(min = 0, max = 255)
    val remark: String? = null
)

@Serializable
data class UpdateUserRequest(
    @property:Min(1)
    val id: Long,

    @property:Size(min = 3, max = 64)
    val username: String? = null,

    @property:Size(min = 2, max = 32)
    val nickname: String? = null,

    @property:Min(1)
    val deptId: Long? = null,

    val postIds: List<Long>? = null,

    @property:Email
    val email: String? = null,

    @property:Pattern(regex = "^1\\d{10}$", message = SystemErrorCodes.MOBILE_INVALID)
    val mobile: String? = null,

    @property:Min(0)
    @property:Max(2)
    val gender: Int? = null,

    @property:Min(0)
    @property:Max(1)
    val status: Int? = null,

    @property:Size(min = 0, max = 255)
    val remark: String? = null
)

@Serializable
data class UpdatePasswordRequest(
    @property:Min(1)
    val id: Long,

    @property:NotBlank
    @property:Size(min = 8, max = 128)
    val newPassword: String
)

@Serializable
data class UpdateStatusRequest(
    @property:Min(0)
    @property:Max(1)
    val status: Int
)

/**
 * 改自己的密码必须带旧密码：只凭登录态就能改，会话一旦被盗（XSS、借用电脑），
 * 对方顺手改掉密码就把真正的主人锁在外面了。
 */
@Serializable
data class ProfileUpdatePasswordRequest(
    @property:NotBlank
    val oldPassword: String,

    @property:NotBlank
    @property:Size(min = 8, max = 128)
    val newPassword: String
)

/**
 * 改自己的资料。**只有展示类字段** —— 不复用 [UpdateUserRequest]：那是管理员编辑别人用的，
 * 带着 deptId / status，复用等于让任何后台账号自己换部门（数据权限按部门划）或改自己的状态。
 */
@Serializable
data class ProfileUpdateRequest(
    @property:Size(min = 2, max = 32)
    val nickname: String? = null,

    @property:Email
    val email: String? = null,

    @property:Pattern(regex = "^1\\d{10}$", message = SystemErrorCodes.MOBILE_INVALID)
    val mobile: String? = null,

    @property:Min(0)
    @property:Max(2)
    val gender: Int? = null,
)
