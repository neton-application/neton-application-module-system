-- 全局设置表（SYSTEM_CONFIG_SPEC）。system 模块的第一条迁移。
--
-- 命名用 settings 而不是 configs：本系统同时存在 config/*.conf（部署期，改了要重启）
-- 与本表（运行期，后台改完立即生效）。两者生命周期不同，共用一个词会让
-- 「改一下配置」这句话无法回答改的是哪个。
--
-- 归属：配置是全局概念，不是基础设施概念 —— 基础设施是文件存储、任务、日志这类东西。
-- 表和读写它的代码都在 system，迁移也跟着放这里；模块自管自己的 SQL。
--
-- 顺序：infra dependsOn system，故本文件先于 infra 的迁移执行。这里只建自己的表，
-- 不碰任何 infra 拥有的对象（system_menus 由 infra V001 建，那时它还不存在）。

CREATE TABLE IF NOT EXISTS system_settings (
    id          BIGSERIAL PRIMARY KEY,
    -- 归属模块，如 member / cs / payment
    category    VARCHAR(64)  NOT NULL,
    setting_key VARCHAR(128) NOT NULL,
    -- 一律存字符串，真实类型由代码里的 SettingDefinition 决定；
    -- 数据库不做类型分裂，免得为一个布尔值再开一列
    value       TEXT         NOT NULL,
    -- 冗余自代码定义，供后台渲染控件；权威仍在代码
    value_type  SMALLINT     NOT NULL DEFAULT 0,
    name        VARCHAR(128) NOT NULL,
    remark      TEXT,
    created_at  BIGINT       NOT NULL DEFAULT 0,
    updated_at  BIGINT       NOT NULL DEFAULT 0
);

-- key 全局唯一：两处写同一个 key 会互相覆盖，运行期只表现为「改了没生效」，极难定位
CREATE UNIQUE INDEX IF NOT EXISTS uk_system_settings_key ON system_settings (setting_key);
CREATE INDEX IF NOT EXISTS idx_system_settings_category ON system_settings (category);
