# 用户资料跨上下文查询设计

## 边界与契约

新增 `j-store-user-api` 作为 User 上下文发布语言：

```kotlin
fun interface UserProfileQueryService {
    fun findById(userId: Long): UserProfileInfo?
}

data class UserProfileInfo(
    val userId: Long,
    val nickname: String,
    val phoneNumber: String,
    val status: UserProfileStatus,
)
```

契约只包含跨上下文所需标量，不暴露 `UserAccount`、密码、会话或 User Repository。`phoneNumber` 必须符合规范 E.164 的结构约束，校验错误不回显原始号码。

## 部署拓扑

```text
模块化单体
Order/Shop ACL -> UserProfileQueryService(local bean) -> UserProfileReader -> UserAccountRepository

微服务集群
Order/Shop ACL -> HttpUserProfileQueryService
               -> GET /internal/api/users/{id}/profile
               -> UserProfileReader -> UserAccountRepository
```

`j-store-user-client-spring` 提供远程适配器自动配置：

- `jstore.user-query.mode=local`（默认）：User boot 将本地 `UserProfileReader` 适配为消费方 `UserProfileQueryService`。
- `jstore.user-query.mode=remote`：不创建本地消费 Bean，要求 `remote.base-url` 和 `remote.token`，只创建 HTTP 实现。

`UserProfileReader` 是提供方读取本地 User 仓储的能力，不实现消费方契约。因此同一组合应用切换到 `remote` 时不会被本地 Bean 截获，而独立 User 服务仍可用 reader 对外提供资料。User 服务端只有在 `jstore.user-query.server.enabled=true` 时暴露内部端点，并强制要求 `jstore.user-query.server.token`。凭证通过 `Authorization: Bearer ...` 传递；该机制是当前仓库缺少统一服务身份基础设施时的最小安全边界，生产集群应在网络层叠加 mTLS、NetworkPolicy 或服务网格授权。

## 消费方 ACL

Order domain 定义 `UserService` 和订单本地的 `UserInfo` 映射。`OrderService.createOrder` 先查询 ACTIVE 用户，再调用工厂；工厂只接收已经取得的不可变用户快照。`OrderCreateCMD` 删除 `buyerPhone` 和 `buyerName`，防止 Controller 或客户端伪造权威资料。

Shop 保留应用层 `UserAccountLookup`，Boot 适配器改由 `UserProfileQueryService` 判断账号是否存在，从而解除对 `UserAccountRepository` 的跨上下文直连。

买家订单详情与取消用例同时接收认证用户 ID，并在返回或修改聚合前检查 `order.buyerInfo.uid`。不匹配时返回 `ORDER_NOT_FOUND`，避免泄露订单是否存在。公开订单响应只返回买家 ID；昵称和已验证手机号仅保留在订单内部快照及受控交易协作中。

## 失败语义

| 场景 | 行为 |
|---|---|
| 用户不存在 | API 返回 404；客户端映射为 `null`；订单返回 `Order.Buyer.Invalid` |
| 用户 DISABLED | 返回资料和状态；订单 ACL 不将其视为有效买家 |
| 内部凭证缺失或错误 | API 返回 401，响应不含资料 |
| HTTP 超时、5xx、反序列化失败或手机号格式非法 | 抛出用户资料依赖异常，事务失败，不映射为 404 |
| 响应为空或响应用户 ID 不匹配 | 抛出用户资料依赖异常，不使用响应资料 |
| remote 模式缺少 URL/凭证 | Spring 启动失败，避免运行期静默降级 |
| 买家访问或取消他人订单 | 返回 `ORDER_NOT_FOUND`，不返回资料、不保存订单 |

## 事务与一致性

用户资料查询是创建订单事务前段的同步查询。订单保存的是创建时快照，因此用户后续修改昵称或手机号不会重写历史订单。收货人联系方式继续来自本次订单请求，两者语义独立。

远程调用与订单数据库事务不构成分布式事务。查询成功后 User 资料立即变化属于允许的快照竞态；订单记录的是实际读取到的版本。依赖失败时不创建部分订单。

## 验证

- API 单元测试验证 DTO 约束和本地查询映射。
- HTTP Controller 测试验证正确、缺失和错误内部凭证以及 404。
- HTTP 客户端测试验证成功、404、401/5xx、空响应、非法资料、用户 ID 不匹配及配置 fail-fast。
- Order 应用服务测试验证资料冻结、用户缺失/禁用时无保存。
- Order 授权与 Controller 契约测试验证越权读取/取消被拒绝，创建请求不接受、响应不返回买家资料字段。
- Shop 配置/应用测试验证账号查询通过发布契约完成。
- User Boot 组合测试验证 local/remote 只产生一个消费方实现，同时保留提供方 reader；日志测试验证验证码和完整手机号不泄露。
- 模块测试和 `./scripts/quality-gate.sh` 验证依赖边界与回归。
