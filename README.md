# neton-application-module-system

Neton canonical 核心模块：系统管理（用户/角色/菜单/部门/岗位/字典/公告/登录日志/操作日志/认证）。

- 外部 canonical 模块，由 `neton-application` 及各发行版以源码子项目聚合，也可独立编译。
- moduleId：`system`；不持有迁移（SQL 由 module-infra 合并持有，DB-MIG-7A）。
- 规范见 neton-application `docs/ENGINEERING_RULES.md`。

## 消息通道

- 短信：`SmsProvider`（HTTP 接口）。
- 邮件：`EmailProvider`，两种发信方式由邮箱账号的 `vendor` 决定：`smtp`（走应用 bind 的 `neton.http.mail.SmtpTransport`，
  POSIX 平台是 libcurl 实现，支持 465 SSL / 587 STARTTLS）或厂商 HTTP 接口（resend / sendgrid / mailgun / brevo / http）。
  模板正文首行 `Subject: …` 作主题，含 HTML 标签按 HTML 发。
- 验证码：`MessageSendLogic.sendVerificationCode`（短信）/ `sendEmailCode`（邮件），码存 Redis 5 分钟，
  有模板走模板，没模板用启用的通道发默认文案，都没有只落 `system_message_logs`（dev）。
