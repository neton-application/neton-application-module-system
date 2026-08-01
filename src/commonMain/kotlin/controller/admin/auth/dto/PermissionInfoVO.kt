package controller.admin.auth.dto

import kotlinx.serialization.Serializable
import controller.admin.menu.dto.MenuVO

@Serializable
data class UserInfoVO(
    val userId: String,
    val username: String,
    val nickname: String,
    val avatar: String = "",
    /**
     * 登录后的落地页。没有任何地方给它赋值，它就是这个常量，所以它必须和前端的
     * 首页一致——前端拿到什么就跳什么。
     *
     * 曾经是 "/analytics"（vben 早期模板的默认首页，这套后台里并不存在）。那个
     * 错值一直没暴露，是因为 KSP 生成的响应序列化器当时用裸 Json（encodeDefaults
     * = false），凡是「当前值恰好等于声明默认值」的字段都会被整个丢掉：前端收不到
     * homePath，于是走自己的 `|| '/dashboard'` 兜底，看着一切正常。
     * ControllerProcessor 把 encodeDefaults 修正为 true 之后，这个字段开始如实
     * 下发，错值才第一次生效。
     */
    val homePath: String = "/dashboard"
)

@Serializable
data class PermissionInfoVO(
    val user: UserInfoVO,
    val roles: List<String>,
    val permissions: List<String>,
    val menus: List<MenuVO>
)
