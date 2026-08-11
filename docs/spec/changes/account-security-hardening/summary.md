# 账号安全硬化交付摘要

## 已交付行为

- 消费者账号接口改为 `/api/users/me` 自助模型，删除所有可由请求选择其他 `UserId` 的查询、启停和强制下线 HTTP 路由。
- 注册前必须完成手机号 challenge；Redis 仅保存 HMAC-SHA256，challenge 与手机号绑定、一次性消费、五分钟过期，并限制一分钟内重复发送。
- 登录对不存在账号和密码错误返回同一错误，使用 Redis 共享十五分钟失败窗口；成功登录清除失败计数。
- 每次登录创建独立 session。Refresh Token 仅以 SHA-256 摘要保存，rotation 由 Lua 原子比较替换，重放撤销该 session。
- 密码修改、账号禁用和强制下线在数据库提交前递增 session epoch，使该用户全部 Access/Refresh Token 在下一次请求立即失效；Redis 撤销失败会回滚账号变更。
- JWT 使用不同的 Access/Refresh HS256 密钥，强制校验 issuer、audience、kid、type、sid 和 session epoch；认证 SDK 每个受保护请求还检查 Redis session。
- 用户 ID 完全由应用 Snowflake 分配，JPA 不再声明数据库生成；迁移删除 ID 默认值并把手机号列扩展为 E.164 所需的 16 字符。

## 验收证据

- `UserAccountControllerContractTest`：`/me` 只能使用认证上下文用户，旧任意用户和消费者管理路由均为 404，匿名方法显式标注 `@SkipLogin`。
- `UserAccountServiceTest`：验证码前置、统一登录错误、共享限流编排、独立 session、摘要 rotation、重放拒绝和单 session 退出。
- `TransactionalUserAccountUseCaseTest`：验证全量会话撤销发生在数据库提交前，且 Redis 撤销失败会触发数据库回滚。
- `RedisAccountSecurityIntegrationTest`：真实 Redis 验证 challenge HMAC、手机号绑定、一次性、过期、发送限流和共享登录失败窗口。
- `RedisTokenStoreIntegrationTest`：真实 Redis 验证多会话、并发 rotation 最多一个成功、重放撤销和 epoch 全量撤销。
- `JwtTokenProviderPropertyTest` 与认证 SDK 测试：双密钥隔离、claims 往返、issuer/audience/kid/type 校验以及服务端 session 拒绝。
- `UserAccountRepositoryPostgresTest` 与 `AccountSecurityMigrationTest`：真实 PostgreSQL 验证 Snowflake ID 往返、ID 无默认值和 16 字符手机号列。
- `./gradlew test --no-daemon --console=plain`：最终源码状态通过，128 个 task，2 executed、126 up-to-date；此前冷增量运行同样通过。
- `./scripts/quality-gate.sh`：治理契约通过，spec-dev 28 项测试通过；secret-scan 配置测试因仓库既有 `.qoder/repowiki/en/content/API Documentation/After-Sale Processing API.md` 缺失而失败。
- 门禁未执行到的 tooling 测试已单独运行，2 项通过。

## 部署边界与残余事项

- `local`/`dev` profile 提供日志短信发送器；生产环境必须提供真实 `PhoneVerificationCodeSender` Bean，否则用户服务无法装配。具体短信供应商、送达回执和供应商级限流不在本次范围内。
- 平台管理员身份、独立签发者和 MFA 尚未实现。账号启停与强制下线只保留应用端口，不再暴露给消费者 HTTP API。
- 本次属于未上线系统的破坏性切换：旧 JWT 和 Refresh Token 全部失效，旧客户端必须改用 challenge 注册和 `/me` 路由。
- 按仓库治理规则，认证与迁移变更仍需未参与实现的独立评估者和人工批准；本摘要不是发布或合并批准。
