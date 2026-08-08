package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Column
import neton.database.annotations.CreatedAt
import neton.database.annotations.Id
import neton.database.annotations.Table
import neton.database.annotations.UpdatedAt

/**
 * 全局配置行（SYSTEM_CONFIG_SPEC §5）。
 *
 * [value] 一律存字符串，真实类型由代码里的 `SettingDefinition` 决定 ——
 * 数据库不做类型分裂，免得为一个布尔值再开一列。
 */
@Serializable
@Table("system_settings")
data class SystemSetting(
    @Id
    val id: Long = 0,

    /** 归属模块，如 member / cs / payment */
    val category: String,

    @Column(name = "config_key")
    val configKey: String,

    val value: String,

    /** 冗余自定义，供后台渲染控件；权威仍是代码里的定义 */
    @Column(name = "value_type")
    val valueType: Int = 0,

    val name: String,

    val remark: String? = null,

    @CreatedAt
    @Column(name = "created_at")
    val createdAt: Long = 0,

    @UpdatedAt
    @Column(name = "updated_at")
    val updatedAt: Long = 0,
)
