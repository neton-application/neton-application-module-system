# 网络准入（IP 白名单）

`module-system` 启动时注册网络准入策略。应用的 `application.conf` 或环境覆盖文件配置：

```toml
[security.network.admin]
enabled = true
allowlist = "203.0.113.10,2001:db8:1234::/48"

[security.network.app]
enabled = false
allowlist = ""
```

示例是文档保留地址，必须替换成自己的出口 IP/网段。支持严格的 IPv4、IPv6、CIDR，逗号分隔，最多 256 项；不支持 DNS、端口、通配符、IPv6 zone ID。IPv4-mapped IPv6 使用显式 IPv6 条目，不自动与 IPv4 合并。

按框架解析出的路由组 `admin` / `app` 匹配，不根据用户提交的路径前缀猜测。配置的组名必须与部署一致；其他组不受这两份策略限制。管理组中的登录、刷新 Token、已认证管理 API 均受限，匿名注解不能绕过。路由不存在时仍返回 404，CORS 预检仍走框架预检；这两者不执行认证或业务处理。

启用且列表为空表示全部拒绝，不隐式放行本机。配置非法会阻止启动。未命中返回 HTTP 403 / 业务码 PERMISSION_DENIED，先于认证、路由限流和 handler；不会校验 Token、查询用户或计算密码哈希。准入使用启动时编译的内存策略，不查库、不解析 DNS，也不逐次打印专用拒绝日志；原有 HTTP 访问日志策略仍适用。

反向代理部署另需在实际生效的 `routing.conf` 设置可信对端：

```toml
[ratelimit]
trusted_proxies = "127.0.0.1,::1"
```

白名单和路由限流共用框架请求的 `clientAddress`：从 TCP 对端出发，仅可信代理可提供 XFF，按右至左解析。不读取 X-Real-IP。Caddy/Next.js 仍须保证可信转发链与直连端口隔离；本设置不会自动修改代理或防火墙。

配置在启动时加载，变更后需重启全部实例。建议先核对正常请求的可信来源、离线验证新配置，再滚动应用。误配置锁住管理端时，从受控服务器终端修改该配置并重启；不存在可远程调用的绕过接口。可以使用配置系统的环境/命令行覆盖，但应核对有效配置优先级。

本版本不通过通用数据库设置表动态修改白名单：该表的容错回默认值语义不适用于安全策略。后台在线编辑、当前管理员防锁检查、多实例原子传播属于后续管理能力，尚未提供。网关 API Key 自身的白名单继续独立执行；本次不迁移其旧来源解析或会员审计 helper。

框架新增 `RequestAdmissionGate`（前置准入）和 `HttpRequest.clientAddress` / `ClientAddressResolver`（请求内缓存来源）。使用已发布依赖的下游必须先发布并升级相应框架与 module-system 包；仅改上游源码不会改变线上行为。
