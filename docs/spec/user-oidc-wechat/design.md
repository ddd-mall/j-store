# OAuth / OIDC 与微信登录设计候选

状态：用户已确认方向，本地实现和自动化回归已建立；整体安全验收仍有未决项，见 [交付记录](summary.md)。验收以 [requirement](requirement.md) 为准。

## 调研基线与变更起点

- `UserAccount` 要求已验证手机号、昵称、密码哈希；`UserAccountService` 已实现密码校验、限流和账号状态检查。
- `TransactionalUserAccountUseCase` 编排账号事务及 Redis 会话撤销；数据库与 Redis 是两个资源，当前实现选择撤销失败阻止安全变更提交、提交失败允许额外下线。
- 当前 `JwtTokenProvider` 和 `RedisTokenStore` 是自定义 JWT 与会话方案，不是 OAuth 授权服务器，也没有 OIDC discovery、授权码、客户端注册、用户同意或外部身份绑定。
- Authentication SDK 的 `AccessTokenVerifier`、`AuthenticatedPrincipal` 和会话校验端口可承接新令牌；业务依赖的认证域内账号语义无需改变。
- 现有规格中的 `OAuth/OIDC 范围外` 属于先前切片边界；新需求须通过本切片 delta 明确新增能力，不能修改旧证据来声称已交付。

## 协议框架与模块职责

采用 Spring Authorization Server，版本由现有 Spring Boot 3.5 BOM 管理；不升级 Boot、不直接引入 Spring Security 7。正式接入前以 `dependencyInsight` 验证实际兼容矩阵，不能仅依据网上最新版文档决定依赖。

| 模块 | 新职责 |
|---|---|
| user-domain | 外部身份绑定及不变量，类型化 provider/app/openid 标识，仓储与认证端口 |
| user-application | 本地凭证验证、微信登录解析、手机号绑定/创建编排、统一撤销用例；不直接依赖授权服务器类 |
| user-infrastructure | 微信 HTTP ACL、绑定持久化、共享临时状态、授权服务和令牌存储适配器 |
| user-boot | 协议 FilterChain、最小 HTML 页面、CSRF、配置和事务装配；框架协议类型止于此边界及 infrastructure |
| authentication-spring-sdk | 维持业务认证入口；通过新 AccessTokenVerifier 验证本地 OIDC Access Token |
| root boot | 组合装配、数据库基线/初始化快照、配置及跨模块集成测试 |

协议框架允许拥有技术持久化 Store；不把协议授权记录伪装成账号聚合，也不把 Spring Security UserDetails 或 OAuth2Authorization 放进领域接口。

## 对外路径候选

| 路径 | 作用与控制 |
|---|---|
| `/.well-known/openid-configuration` | 固定 issuer 的实际能力元数据 |
| `/.well-known/oauth-authorization-server` | OAuth 元数据 |
| `/oauth2/authorize` | 标准授权码入口，客户端与回调校验先于登录跳转 |
| `/oauth2/token` | 仅 authorization_code / refresh_token；client 认证和 PKCE |
| `/oauth2/jwks` | 公钥，不含私钥字段 |
| `/oauth2/revoke`、`/oauth2/introspect` | 按 client 所有权操作，匿名不可探测他人 token |
| `/userinfo` | 仅对应授权范围的账号资料 |
| `/connect/logout` | 验证 id_token_hint、会话及预注册返回 URI |
| `/login` | GET 页面 / POST 本地密码登录；CSRF 和限流，成功更换 session ID |
| `/consent` | 显示已注册客户端和 scope；提交绑定当前授权请求并校验 CSRF |
| `/login/wechat` | 发起扫码，保存一次性 state；只使用服务端保存的继续路径 |
| `/login/wechat/callback` | 校验 state 和浏览器绑定，再兑换 code |
| `/login/wechat/onboarding` | GET 表单 / POST 手机号验证与账号创建或绑定；CSRF |

具体协议请求解析和标准错误交由框架；只对标准 token/revoke/introspection 等协议要求的路径按框架方式处理 CSRF，不全局禁用。业务 API 仍返回 JSON 401，不重定向到登录页。允许的 CORS origin 由客户端配置显式列举，禁止凭证加通配 origin。

## 外部身份与首次登录

`ExternalIdentityBinding` 是独立持久化身份绑定，以本地 UserId 引用账号。数据库约束：

- 唯一 `(provider, app_id, openid)`，防止一个微信身份关联多个账号。
- 唯一 `(provider, app_id, user_id)`，防止本切片中静默替换既有微信身份。
- `unionid` 可空并附开放平台范围，不用其自动推导跨 app 账号关系。
- `user_accounts.phone_number` 保持唯一。

回调无绑定时保存服务端 onboarding 状态：随机 ID、浏览器绑定、external identity、原始授权上下文、创建时间和到期时间；浏览器 Cookie 只持有不透明引用，不能自行选择账号或微信身份。

手机号挑战新增明确用途与 onboarding 引用，防止普通注册验证码成为不相关微信绑定证明。绑定表单提交明确确认意图：已有账号验证手机号后绑定；新账号还需昵称和符合当前强度要求的密码。客户端传入 userId/openid/unionid 一律不作为权威输入。

上游兑换在本地写事务外执行。提交前通过 Redis 原子消费 onboarding；短数据库事务先锁定已有手机号账号，再消费用途化验证码、创建必要账号、写绑定与注册事件 Outbox。SQL 回滚不恢复已消费凭证，失败需要重新发起流程。提交后不再依赖 Redis 写入来确认绑定；若响应丢失，重新扫码会按已提交绑定读取同一账号，不重复创建或换绑。

## 授权、刷新和撤销

浏览器认证会话、OAuth 授权与 Refresh family 分别有稳定标识；会话 epoch 仍是用户级撤销屏障。每次授权、码兑换、刷新和 API 认证都验证会话及 epoch，登录 Cookie 不是跳过账号状态检查的依据。

授权服务不能直接使用默认明文 JDBC token 列来满足“仅摘要保存”的验收。实现须提供有测试覆盖的协议 Store 适配：

- 授权码、Refresh Token 以带用途的摘要索引查询，持久化元数据不包含其明文或可还原序列化副本。
- 兑换在数据库锁/CAS 保护的同一状态转换内完成；框架先 read 后 save 的默认流程不能被视为已证明单次消费。
- 旧 Refresh 摘要及 family ID 保留至绝对到期，识别重放后原子撤销 family；禁止仅覆盖当前摘要而丢失重放归属。
- family 撤销状态进入业务 token 校验；JWT 未到期不代表仍有效。浏览器登录可以保留，但被撤销 family 下的 token 不得使用。
- 实际向浏览器返回 token 前完成必要的状态保存；存储失败不返回可用凭证。
- Redis/数据库故障拒绝受保护请求。账号安全变更沿用先推进撤销屏障、再提交数据库的保守策略，不回退 epoch。

框架扩展点和事务行为须先用风险验证测试确定；若无法在既定框架版本中满足并发或不可逆摘要要求，应报告设计阻塞，不通过降低安全目标来交付。

## 密钥与配置

新增 catalog alias 仅在实际启用时加入。授权服务器、Security Test、共享 session 等依赖通过统一 Platform 管理，补齐依赖验证元数据和许可证证据。生产签名使用配置的非对称密钥和稳定 kid，不在本任务中生成或读取真实私钥。

客户端配置包含 client ID、类型、secret 摘要、redirect/post-logout URI、scope、audience、CORS origin。微信配置包含 enabled、appId、secret 外部引用、callback URI、连接/读取超时；生产微信 API host 固定，不接受请求参数改变。假 HTTP server 仅在测试构造中注入。

本次不需要微信后续 API 调用，因此兑换结果只在当前请求内使用，丢弃上游 access/refresh token。对微信域名调用的 query、响应体及认证页面提交禁用敏感日志记录，error 仅保留受控分类和 correlation ID。

## 外部资料与证据边界

- [Spring Authorization Server 配置模型](https://docs.spring.io/spring-authorization-server/reference/configuration-model.html)：协议端点和扩展点。实际依赖版本由本地 Boot BOM 验证。
- [Spring 公共客户端 PKCE 指南](https://docs.spring.io/spring-authorization-server/reference/guides/how-to-pkce.html)：public client 与 confidential BFF 的刷新策略。
- [OAuth 安全最佳实践 RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html)：回调校验、PKCE 和刷新重放防护。
- [微信网站应用登录文档](https://developers.weixin.qq.com/doc/oplatform/Website_App/WeChat_Login/Wechat_Login.html)：2026-11-08 通过官方 HTTPS 地址读取压缩 HTML，确认 qrconnect、snsapi_login、服务端 code 兑换和 openid/unionid 契约。固定假服务验证成功、拒绝和身份不一致；真实平台联调仍未执行。

## 审查重点

公共旧 token 端点删除、scope 资料披露、手机号验证后绑定既有账号、session/family 撤销范围，以及授权服务器 FilterChain 与 SDK 的职责变化须人工确认。实现结束后仍需针对最终候选的独立评估，规格确认不等于代码安全批准。

## 实现收敛

- `LoginPageController`、`ConsentPageController`、`WechatLoginController` 分别负责登录、同意和微信流程；共享 HTML 输出转义并禁止缓存和嵌入。
- `AccountSessionStore` 只管理账号会话，Refresh family 由协议 Store 管理。旧 TokenProvider、AuthTokenPair、HS256 JWT 实现和专属测试被标准协议实现与测试替代。
- `ProtocolTransactionFilter` 缓冲协议 body 和跳转，提交后才返回凭证。受控 OAuth 错误仍提交必要的重放撤销；提交失败清空响应并返回 503。
- 用户同意 POST 另行验证 Spring CSRF token；协议 CSRF 豁免不能绕过它。`prompt=login` / `max_age` 使用一次性继续标记，并忽略 SavedRequest 的 `continue` 参数进行意图匹配。
- Access Token 的 sid 表示账号 session；ID Token 保留框架 OIDC sid 语义。登出另行校验持久化授权与当前账号 session 的关联，不把节点本地 SessionRegistry 当作跨节点身份权威。
- 令牌生成注入 Clock，JWT 使用零过期容差；Refresh 的绝对到期时间在轮换中保持。当前 family 刷新后旧 Access Token 失效。
- OAuth JSON 沿用 Spring Security 类型白名单，仅增加 epoch/auth_time 所需的不可变 Long 标量 mixin，不启用任意类型反序列化。
- 仅支持本切片的 Bearer Token 契约；拒绝 DPoP 请求及业务端的 cnf 绑定令牌。Discovery 不宣称未启用的 DPoP、mTLS、device 或 client-credentials 能力；introspection/revocation 仅声明 client_secret_basic。
