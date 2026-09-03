# 认证域内账号标识语义收敛

## MODIFIED

### 统一账号与商户成员关系：账号标识范围

替换 `unified-account-merchant-membership` 验收标准 1 中“全局 `UserId`”语义：

- 注册和登录返回当前认证域内稳定的 `UserId`；其唯一性不跨站点、Issuer 或认证域承诺。
- 业务入口从 `AuthenticatedPrincipal` 取得认证域和域内账号标识，客户端不能提交或覆盖这些值。
- 当前站 JWT 校验是 `AccessTokenVerifier` 的一个实现；以后可由 OIDC 或其它站点 Issuer 实现同一端口。

### 交易买家身份

- Order 中的买家标识表示“指定认证域内的账号标识”，认证域与数字标识共同构成账号引用。
- 买家读取、取消和售后访问必须同时匹配认证域与域内账号标识；不得用裸数字 ID 跨认证域查询用户或交易。
- Trade 必须冻结发起交易的认证域，并把带作用域的买家引用传递给 Order。

### 集成消息

- 跨上下文消息不得把裸 `buyerId` 表达为全球身份。
- Trade→Order 创建命令必须携带认证域和域内买家标识，并作为一个完整账号引用被消费。

## NOT IN SCOPE

- 本次不增加多站点账号表、跨站账号合并、Issuer 路由或 OIDC 网络配置。
- 本次不承诺不同认证域中的账号可互相查询；User 上下文查询仍只解析当前部署认证域。

## ACCEPTANCE

1. 认证拦截器依赖 `AccessTokenVerifier` 并向 Controller 注入 `AuthenticatedPrincipal`，不再注入裸 `UserId`。
2. JWT 实现返回包含配置 Issuer 和域内 `UserId` 的 principal。
3. Trade→Order 消息中不存在顶层裸 `buyerId`，而是传递带认证域的买家账号引用。
4. Order 冻结并持久化买家认证域；同一数字 ID 在不同认证域中不会通过买家访问校验。
5. User 资料查询契约明确只在当前认证域解析数字 ID。

## ROLLBACK

项目仍处于内部开发期。本变更直接更新内部认证、消息和持久化契约，不提供旧消息版本或旧数据库兼容层；回滚方式是撤销本变更并重建开发数据库。
