# 本地接入与运行说明

实现状态、验证结果和发布阻塞见 [summary](summary.md)。本文件描述候选的配置与调用方法，不授权生产部署或真实平台写入。

## 必要配置

沿用项目 PostgreSQL、Redis 和短信发送端口配置，另外显式提供：

| 环境变量 | 含义 |
|---|---|
| `JSTORE_OIDC_ISSUER` | 稳定外部 issuer，无尾斜杠；生产必须 HTTPS |
| `JSTORE_OIDC_JWK_SET_LOCATION` | 外部 RSA JWK Set 资源位置，例如 `file:/absolute/path/signing-jwks.json` |
| `JSTORE_OIDC_ACTIVE_KEY_ID` | JWK Set 内用于签名的 RSA 私钥 kid，至少 2048 位 |
| `JSTORE_OIDC_CLIENT_ID` | 预注册 client ID，默认 web |
| `JSTORE_OIDC_CLIENT_REDIRECTS` | 精确回调 URI 列表，逗号分隔，不支持通配 |
| `JSTORE_OIDC_CLIENT_POST_LOGOUT_REDIRECTS` | 精确登出返回 URI 列表，可空 |
| `JSTORE_OIDC_CLIENT_SECRET_HASH` | Public client 留空；confidential BFF 设置带 `{bcrypt}` 前缀的 BCrypt 摘要 |
| `JSTORE_OIDC_CLIENT_SCOPES` | 默认 `openid,profile`；只有 confidential BFF 才能配置 `offline_access` |
| `JSTORE_OIDC_CLIENT_AUDIENCES` | 为该 client 签发的业务资源 audience，默认 j-store-api |
| `JSTORE_OIDC_RESOURCE_AUDIENCE` | 当前业务 API 接受的 audience，默认 j-store-api |
| `JSTORE_OIDC_CLIENT_CORS_ORIGINS` | 显式允许的前端 origin，可空，不支持通配 |

`.env.example` 仅包含占位符。真实私钥、微信 secret、client secret 和短信验证码不能进入 Git 或聊天。本次没有生成或配置生产私钥。私钥文件应通过部署秘密管理系统提供，并限制文件权限；多实例必须使用一致的 issuer、客户端配置及签名材料。

多个客户端可通过 Spring 配置的 `jstore.oidc.clients[n]` 列表声明，字段为 id、name、secret-hash、redirects、post-logout-redirects、scopes、audiences、cors-origins。配置在启动时同步到数据库；已移出配置的客户端即使还有历史数据库记录也不能认证。

生产要求真实短信适配器、Redis 主节点读写及可靠持久化。撤销状态不可用时拒绝认证，不能把认证读改为允许陈旧数据的副本读。浏览器会话由 Spring Session Redis 管理，空闲 30 分钟、绝对 12 小时；账号 session 用于令牌撤销，最长 7 天。不要独立恢复旧授权表或把撤销屏障回退到历史快照。恢复或更换 issuer 时应废弃对应旧会话，再按明确的环境恢复流程操作。

## 标准登录流程

1. 客户端生成随机 `state`、`nonce` 和 PKCE verifier，并由 verifier 的 SHA-256 派生 base64url challenge。
2. 浏览器跳转 `/oauth2/authorize`，携带 `response_type=code`、client_id、精确 redirect_uri、scope、state、nonce、code_challenge 和 `code_challenge_method=S256`。
3. 用户在 `/login` 使用本地密码或微信扫码，查看并提交同意页面。拒绝不会签发授权码。
4. 客户端先验证返回 state，再通过 `/oauth2/token` 以 code、同一个 redirect_uri 和 code_verifier 兑换。Confidential client 使用 HTTP Basic；public client 发送 client_id。
5. 客户端验证 ID Token 的签名、iss、aud、exp、nonce；调用业务 API 时只发送 Access Token。UserInfo 按当前 Access Token 的 scope 返回昵称/已验证手机号。

Public client 不获得 Refresh Token。浏览器需要长期登录时使用 confidential BFF，服务端保存 Refresh Token；不要把 client secret 下发浏览器。每次刷新必须原子替换客户端保存的 Refresh Token，复用旧值会撤销整个授权。当前 family 的旧 Access Token 在刷新后立即失效。

登出可调用受保护的 `POST /api/users/me/logout` 撤销当前账号 session，或由浏览器发起 `/connect/logout`，携带最新 ID Token hint 和预注册 post_logout_redirect_uri。登出返回地址不允许由客户端任意指定。改密、禁用和强制下线会撤销用户所有既有 session；重新启用不会恢复旧凭证。

旧 `POST /api/users/login`、`POST /api/users/refresh-token`、自定义 TokenResponse 及 HS256 双 JWT 已移除，客户端必须同步修改。保留手机号注册、资料查询与昵称/密码自助修改。

## 微信网站扫码

启用前由应用所有者准备已审核的网站应用、有效授权回调域名及可访问的 HTTPS 回调，再通过外部秘密配置提供：

```properties
JSTORE_WECHAT_ENABLED=true
JSTORE_WECHAT_APP_ID=<website-app-id>
JSTORE_WECHAT_APP_SECRET=<external-secret>
JSTORE_WECHAT_CALLBACK_URI=<issuer>/login/wechat/callback
```

callback 必须与配置 issuer 精确对应。微信入口使用网站 `snsapi_login`，服务端固定调用微信 code 兑换与 userinfo 端点，验证 openid 一致后丢弃上游 token。连接超时 3 秒，每次响应总等待最多 5 秒，响应体限制 64 KiB；不自动重试一次性 code。

首次扫码后验证手机号并明确确认绑定；已有账号直接复用，新账号必须提供昵称及符合现有规则的密码。页面不展示协议内部挑战编号，表单自动携带。流程失败、超时或提交冲突时重新发起，不能恢复旧验证码、绕过证明或自动覆盖既有绑定。

callback、token、验证码和手机号页面不得记录完整请求/响应；反向代理也必须屏蔽 callback 的 code/state 查询参数。不要启用 JDK HTTP client 的请求/响应调试日志。应用页面禁止缓存和外链资源，并设置 no-referrer。

## 密钥与开发数据

保留旧验证公钥至所有对应令牌和登出 hint 的有效窗口结束；新签名使用 active kid。JWKS 仅返回公钥。缺少签名材料、客户端配置或生产微信参数时启动失败，没有临时生产密钥兜底。

当前基线与初始化快照直接增加 OAuth 客户端、授权、同意、Refresh 历史摘要及身份绑定表；没有增量回填和旧开发数据兼容脚本。已初始化开发库需要按项目规则重建，但本次没有执行任何现有库清空或真实环境迁移。回滚代码后也必须废弃本轮会话与授权，不能恢复已撤销凭证。
