# neton-application-module-system

Neton canonical 核心模块：系统管理（用户/角色/菜单/部门/岗位/字典/公告/登录日志/操作日志/认证）。

- 外部 canonical 模块，由 `neton-application` 及各发行版以源码子项目聚合，也可独立编译。
- moduleId：`system`；不持有迁移（SQL 由 module-infra 合并持有，DB-MIG-7A）。
- 规范见 neton-application `docs/ENGINEERING_RULES.md`。
