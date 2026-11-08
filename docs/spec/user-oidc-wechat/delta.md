# 对现有账号规格的拟议变更

状态：2026-11-08 用户确认按此方向实施，本 delta 为本次实现目标。

## 账号安全硬化

原来源：`docs/spec/changes/account-security-hardening/requirement.md`。

- 将 OAuth/OIDC 和微信网站社交登录从原切片的范围外提升为本次建设范围；原排除并非永久禁止。
- 原验收 11 的 Access/Refresh 双 JWT 密钥策略被替换：Access Token 与 ID Token 使用非对称签名并严格区分用途和 audience；Refresh Token 为随机不透明凭证，仅保存摘要，不能用 JWT 验签替代服务端状态。
- 保留原验收 6–10 的多会话、摘要存储、原子轮换、立即撤销和逐请求校验要求，并扩大到授权码、OIDC browser session 和授权记录。
- 原验收 12 扩展为职责明确的两个边界：授权服务器的协议/登录 FilterChain 只匹配协议与认证 UI；业务 API 仍由 Authentication SDK 和 `@RequireLogin` / `@SkipLogin` 决定。不得引入覆盖业务注解语义的通用登录 Filter 或默认全站重定向。

## 用户公开端点

- 保留手机号验证码申请、账号注册、`GET /api/users/me`、昵称修改和密码修改能力。
- 由授权服务器登录页接入本地密码验证、限流和微信扫码；登录本身只建立认证 Cookie，不直接返回业务 token。
- 拟删除旧 `POST /api/users/login` 与 `POST /api/users/refresh-token` 以及自定义 token pair 响应；客户端统一经 `/oauth2/authorize` 和 `/oauth2/token` 获取标准响应。
- 保留 `POST /api/users/me/logout` 的自助能力，语义更新为撤销当前账号会话及派生授权；OIDC RP 发起登出负责浏览器 Cookie 与受控返回地址。二者共用撤销用例。
- 不保留旧内部 JWT、旧 Refresh Token、旧 token 端点的双轨兼容；内部开发数据可重建，客户端样例与测试必须同步更新。

## 账号与跨上下文身份

- `scoped-account-identity` 的 `authenticationDomain + accountId` 语义保持；`iss + sub` 必须可无损映射为现有 principal。
- `unified-account-merchant-membership` 的同一自然人账号与商户成员关系不变。客户端 ID、scope、微信身份不等于商户权限。
- `user-profile-query` 的已验证手机号和 ACTIVE 用户事实不变；首次微信用户完成手机号验证、提供昵称/密码前不创建正式账号。

## 兼容与恢复

本项目仍按内部开发期策略维护当前数据库基线/初始化快照，不增加旧内部数据回填、双写或兼容端点。实现需列出确切修改的基线文件；本文不授权执行数据库清空、真实迁移或生产写入。

回滚候选代码和开发配置后，开发环境应显式废弃本轮签发的授权及会话并重建对应测试数据；不恢复已撤销会话、不通过恢复旧密钥绕过撤销。真实环境恢复方案须另行明确目标并获得授权。
