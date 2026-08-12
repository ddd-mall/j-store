# 结账可靠异步基础设计

## 核心决策

1. 保留 `DomainEvent`、`IntegrationMessage`、Outbox 和 Delivery Channel 四层结构。
2. `IntegrationMessage.destination` 继续表示稳定逻辑目的地；物理 Topic/Exchange 由发布路由规划生成。
3. 用 `IntegrationPublicationPlanner` 替换只返回全局目标的规划逻辑。规划结果是一组 `IntegrationPublication`，每项包含 transport、逻辑 destination、物理 destination 和 delivery profile。
4. `MessagingProperties.targets` 作为默认投递目标；`routes` 提供按逻辑目的地的显式覆盖。
5. `acceptBefore` 是命令业务语义，通过消息 metadata、Outbox 和 Envelope 原样传播；Outbox relay 不自行丢弃过期关键命令。
6. `deliveryProfile` 是基础设施策略标识，本切片只持久化并路由，不在消息 payload 中固化具体重试次数。
7. `publishedAt` 在 relay 收到目标 ACK 并成功进入本地 mark-published 事务时写入。

## 路由模型

```text
IntegrationMessage(logicalDestination)
  -> IntegrationPublicationPlanner
       -> explicit route deliveries, if configured
       -> default targets, otherwise
  -> one OutboxEntry per publication
```

框架无关模型：

```kotlin
data class IntegrationDeliveryRoute(
    val transportId: String,
    val destination: String,
    val deliveryProfile: String,
)

data class IntegrationRoute(
    val logicalDestination: String,
    val deliveries: List<IntegrationDeliveryRoute>,
)

data class IntegrationPublication(...)
```

Spring 配置使用列表而不是以含点 destination 为 Map key，避免 relaxed binding 歧义：

```yaml
jstore:
  messaging:
    targets: [local]
    routes:
      - logical-destination: payment.commands
        deliveries:
          - transport-id: kafka
            destination: jstore.payment.commands.v1
            delivery-profile: CHECKOUT_CRITICAL
```

## Outbox 数据变化

新增字段：

```text
logical_destination VARCHAR(512) NOT NULL
delivery_profile VARCHAR(64) NOT NULL
accept_before TIMESTAMPTZ NULL
published_at TIMESTAMPTZ NULL
```

现有 `destination` 改为物理 destination。ordering key 仍按逻辑 destination + partition key 生成，使物理 Topic 重命名不会改变业务流身份。

项目未上线，迁移只建立当前结构和约束，不转换旧 Outbox 数据。已有开发数据库应重建 schema；领域事件新写入时使用 `LOCAL_DOMAIN` profile，集成消息由发布规划器给出 profile。

## 稳定消息 ID

新增来源感知函数：

```text
messageName | messageVersion | sourceMessageId | businessKey
```

只保留来源感知函数，所有 Commerce 集成契约统一使用该算法。版本必须由每个消息类型显式传入。输入字段使用长度前缀编码后再生成 UUID，避免来源 ID 或业务键包含分隔符时产生歧义碰撞。

## Checkout 契约变化

- 关键 Command 直接声明可选 `acceptBefore`，不保留重复的序列化别名。
- `InventoryReservedIntegrationEvent` 增加 `reservationExpiresAt`。
- Inventory reserve 用例返回统一过期时间；重复请求返回既有 Reservation 的最早过期时间。

支付准备成功/失败、支付取消和状态未知消息将在后续支付状态机切片交付。本切片不在缺少收银台会话和支付机构契约时制造占位业务事实。

库存预留以 `min(库存 TTL, 销售授权过期时间)` 作为单条 Reservation 的过期时间；跨上下文成功事实必须携带该订单所有 Reservation 的最早过期时间。`ReserveInventoryCommand` 的 `acceptBefore` 同样取最早销售授权过期时间，消费者到达该时间后不再占用 ATP。项目尚未上线，当前领域事实、集成命令与集成事实均从 V1 发布，过期时间为必填字段。

库存用例在等待并取得全部库存行锁后重新读取时钟，并在修改 ATP 前再次检查 `acceptBefore` 与授权过期时间，避免锁等待跨过截止时间后仍创建无效 Reservation。

Outbox 领域模型与数据库共同约束发布时间和消息类型元数据：仅 `PUBLISHED` 记录具有 `publishedAt`，只有集成命令可以具有 `acceptBefore`，领域事件固定使用 `LOCAL_DOMAIN` profile 且没有受理截止时间。

## 已实现边界（2026-08-12）

- 已实现契约、路由规划、Spring 配置、Outbox/Envelope 元数据、Flyway 结构升级和库存时限传播。
- 已删除被 `IntegrationPublicationPlanner` 完整替代且无生产调用的旧 `IntegrationTransportPlanner`，以及无仓库调用的时间参数版稳定 ID 函数。
- Outbox PO 到领域模型采用严格直接映射，不再用 `ifBlank` 修复旧记录；不符合当前 schema/领域约束的数据应立即暴露。
- 已保留全局 `targets` 回退路径；未配置 `routes` 时行为不变。
- 尚未接入具体 Broker 适配器与入站 Inbox，也未实现 Checkout Process Manager、支付准备状态机、提交后唤醒和两阶段关单。
- 因此本切片解决“消息具备可靠路由和时限信息”的基础问题，不宣称已完成跨微服务端到端结账闭环。

## 演进与回滚

- 默认无显式 routes 时，所有消息使用全局 targets；这是模块化单体和默认部署模式，不是历史兼容分支。
- 项目尚未上线，公共契约采用一次性破坏性升级，不保留旧消息类型、旧稳定 ID 算法或可空的库存过期时间。
- `acceptBefore` 仍可空，因为并非所有业务命令都有受理截止时间；该可空性属于业务语义。
- 数据库字段仍通过 Flyway 管理，以保证环境可重建和结构变更可验证。
