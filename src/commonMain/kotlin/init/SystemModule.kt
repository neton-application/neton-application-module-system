package init

import neton.core.annotations.Module

/** system 模块声明锚点（MANIFEST-P3）。
 *  @Logic: 9 个纯单-Logger logic (User/Role/Menu/Log/Dept/Post/Notice/NotifyMessage/
 *  MessageTemplate); runtime: init.SystemRuntimeBootstrap (Table 注册 + jwt +
 *  superAdmin + providers + 7 个带依赖的复杂 logic); 路由由 KSP manifest。
 *  system 不持有 migration (SQL 由 infra 合并持有, DB-MIG-7A)。 */
@Module
object SystemModule
