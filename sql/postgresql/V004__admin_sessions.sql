-- 管理员会话版本与全局授权版本（spec application/identity-realm §4、评审 R04/R05）。
--
-- 和 system_mfa 一样按 user_id 关联、不建外键：system_users 由 module-infra 的基线创建，
-- 而迁移按模块顺序执行，system 的脚本先于 infra 运行。没有行等价于版本 0。

-- 用户级会话版本：改密、重置密码、全端退出时加一，已签发的令牌（含 sv 声明）立即失效。
CREATE TABLE IF NOT EXISTS system_user_sessions (
    user_id         BIGINT PRIMARY KEY,
    session_version BIGINT NOT NULL DEFAULT 0
);

-- 全局授权版本：角色、菜单、用户角色、相关状态的任何写操作都加一。
-- 各实例按「用户 + 版本」缓存解析后的权限，版本一变即重新计算 —— 撤权即时生效且多实例一致。
CREATE TABLE IF NOT EXISTS system_authz_version (
    id      INTEGER PRIMARY KEY CHECK (id = 1),
    version BIGINT NOT NULL DEFAULT 0
);
INSERT INTO system_authz_version (id, version) VALUES (1, 0) ON CONFLICT (id) DO NOTHING;
