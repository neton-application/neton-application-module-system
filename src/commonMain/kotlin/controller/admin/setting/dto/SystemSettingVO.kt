package controller.admin.setting.dto

import kotlinx.serialization.Serializable

/** 配置项：库里的当前值 + 代码里的定义合并后的视图 */
@Serializable
data class SystemSettingVO(
    val category: String,
    val key: String,
    val value: String,
    /** SettingValueType 序号，后台据此选控件 */
    val valueType: Int,
    val name: String,
    val description: String,
    val defaultValue: String,
    /** true = 当前值就是默认值（后台没改过） */
    val isDefault: Boolean,
)

@Serializable
data class UpdateSystemSettingRequest(val key: String, val value: String)
