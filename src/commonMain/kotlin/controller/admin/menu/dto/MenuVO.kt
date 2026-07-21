package controller.admin.menu.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Max
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class MenuVO(
    val id: Long,
    val name: String,
    val permission: String? = null,
    val type: Int,
    val parentId: Long,
    val path: String? = null,
    val component: String? = null,
    val icon: String? = null,
    val sort: Int,
    val status: Int,
    val visible: Boolean,
    val keepAlive: Boolean,
    val children: List<MenuVO>? = null
)

@Serializable
data class CreateMenuRequest(
    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val name: String,

    @property:Size(min = 0, max = 100)
    val permission: String? = null,

    @property:Min(1)
    @property:Max(3)
    val type: Int,

    @property:Min(0)
    val parentId: Long = 0,

    @property:Size(min = 0, max = 255)
    val path: String? = null,

    @property:Size(min = 0, max = 255)
    val component: String? = null,

    @property:Size(min = 0, max = 100)
    val icon: String? = null,

    @property:Min(0)
    @property:Max(9999)
    val sort: Int = 0,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1,

    // ── 前端表单的展示层字段（vben menu form 会一并提交）。后端 Menu 模型无对应列，
    //    仅为反序列化容忍而声明（严格解析下未知键=400 Validation failed）。──
    val componentName: String? = null,
    val visible: Boolean? = null,
    val alwaysShow: Boolean? = null,
    val keepAlive: Boolean? = null,
)

@Serializable
data class UpdateMenuRequest(
    @property:Min(1)
    val id: Long,

    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val name: String,

    @property:Size(min = 0, max = 100)
    val permission: String? = null,

    @property:Min(1)
    @property:Max(3)
    val type: Int,

    @property:Min(0)
    val parentId: Long = 0,

    @property:Size(min = 0, max = 255)
    val path: String? = null,

    @property:Size(min = 0, max = 255)
    val component: String? = null,

    @property:Size(min = 0, max = 100)
    val icon: String? = null,

    @property:Min(0)
    @property:Max(9999)
    val sort: Int = 0,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1,

    // ── 前端表单的展示层字段（vben menu form 会一并提交）。后端 Menu 模型无对应列，
    //    仅为反序列化容忍而声明（严格解析下未知键=400 Validation failed）。──
    val componentName: String? = null,
    val visible: Boolean? = null,
    val alwaysShow: Boolean? = null,
    val keepAlive: Boolean? = null,
)
