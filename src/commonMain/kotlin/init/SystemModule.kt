package init

import neton.core.annotations.Module

/** system 模块声明锚点（MANIFEST-P3）。
 *  @Logic: 9 个纯单-Logger logic (User/Role/Menu/Log/Dept/Post/Notice/NotifyMessage/
 *  MessageTemplate); runtime: init.SystemRuntimeBootstrap (Table 注册 + jwt +
 *  superAdmin + providers + 7 个带依赖的复杂 logic); 路由由 KSP manifest。
 *  migrations: system_settings 归自己（V001）；system_* 其余表仍在 infra 名下
 *  (DB-MIG-7A 遗留)，搬迁受 infra dependsOn system 的执行顺序约束。 */
@Module(migrations = true)
object SystemModule
