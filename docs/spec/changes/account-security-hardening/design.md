# 账号安全硬化设计

## API 与授权

- 保留 `POST /api/users/phone-verifications`、`register`、`login`、`refresh-token` 为匿名接口。
- 自助接口统一为 `GET /api/users/me`、`PUT /api/users/me/nickname`、`PUT /api/users/me/password` 和 `POST /api/users/me/logout`。
- 删除 `/api/users/{id}` 及其启用、禁用、强制下线等 HTTP 入口。应用层的账号状态用例仍保留，但没有独立平台身份域时不可从消费者 API 调用。
- `UserAccountController` 使用 `@RequireLogin`、`@SkipLogin` 和 `@CurrentUserId`；删除 Servlet `JwtAuthenticationFilter` 及其硬编码白名单。

## 手机号验证与防滥用

应用层定义 `PhoneVerificationGateway`、`PhoneVerificationCodeSender` 和 `LoginAttemptGuard` 端口。Redis 适配器负责：

- 生成 6 位随机验证码和不可猜测的 challenge ID；
- 只存储 `HMAC-SHA256(challengeId + phone + code)`；
- 用 Lua 原子校验并删除 challenge，保证一次性消费；
- 对发送频率和登录失败进行 Redis 计数限流。

`local`/`dev` profile 提供仅用于开发日志的发送器；其他 profile 必须注入真实发送器，否则应用装配失败。

## 会话与 Token

JWT claims 至少包含 `sub/userId`、`sid`、`sev`（session epoch）、`jti`、`type`、`iss`、`aud`、`iat`、`exp`，Header 包含 `kid`。Access 与 Refresh 使用不同 HS256 密钥，避免一种密钥泄露后同时伪造两种令牌；外部密钥管理和非对称签名可在 TokenProvider 端口后继续演进。

Redis 会话键为 `auth_session:{userId}:{sessionId}`，值只保存 Refresh Token SHA-256 摘要和 epoch，TTL 与 Refresh Token 一致。`auth_session_epoch:{userId}` 表示全用户撤销代次：

- 登录读取当前 epoch，创建独立 session；
- 刷新以 Lua 比较旧摘要、epoch 后写入新摘要；
- 密码修改、禁用和强制下线递增 epoch，使所有旧 Token 在下一次认证时失败；
- 请求认证同时要求 epoch 相等且 session key 存在。

## 持久化与迁移

`UserAccountPO.id` 改为应用分配 ID，不使用 `@GeneratedValue`。新增迁移删除 `user_accounts.id` 的序列默认值；不修改已发布迁移。旧 JWT、旧 Refresh Token 和缺少 `sid/sev/iss/aud/kid` 的令牌在部署后全部失效，用户需要重新登录。

## 事务边界

数据库聚合和 Outbox 保持在用户 boot 的事务装饰器中。登录成功后才创建 Redis session；密码修改、禁用和强制下线仅在数据库事务提交后递增 session epoch。Redis 失败不得伪装成数据库原子成功，调用方收到失败并由审计/重试机制处理。

## 回滚

本项目尚未上线，不为不安全的旧接口和旧 Token 提供兼容回滚。代码回滚前必须先停止流量并重新清空所有认证 Redis key；数据库 ID 默认值迁移不可作为旧应用可安全回滚的保证。
