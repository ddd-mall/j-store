# DDD Violation Report — j-store

**Generated**: 2025-07-15
**Scanned modules**: `j-store-common-core`, `j-store-common-spring`, `j-store-order`, `j-store-order-infrastructure`, `j-store-order-boot`, `j-store-goods`, `j-store-goods-infrastructure`

## Summary

| Category | ERROR | WARNING | Total |
|---|---|---|---|
| 1. Cross-Context Imports | 0 | 0 | 0 |
| 2. Infrastructure in Domain | 1 | 0 | 1 |
| 3. DDD Structural | 3 | 2 | 5 |
| 4. Layer Violations | 1 | 2 | 3 |
| 5. ACL Violations | 0 | 0 | 0 |
| **Total** | **5** | **4** | **9** |

---

## 专题分析：OrderService / OrderFactory 与 ACL 层的依赖关系

> 用户重点关注：领域服务 `OrderService` 依赖 ACL 中的服务是否合理？

### 结论：当前架构设计合理，符合 DDD 规范

#### 依赖关系图

```
OrderService (应用服务, service/)
  ├── OrderFactory (领域工厂, domain/order/)
  │     ├── GoodsService (ACL 接口, acl/)
  │     └── GeoAddressService (ACL 接口, acl/)
  └── OrderRepository (仓储接口, domain/order/)
```

#### 分析

**1. `OrderService` 不直接依赖 ACL — ✅ 正确**

`OrderService` 仅依赖 `OrderFactory` 和 `OrderRepository`，不直接引用任何 ACL 接口。它的职责是编排用例（加载聚合 → 执行领域行为 → 保存），不包含业务规则，完全符合应用服务的定位。

```kotlin
// OrderService.kt — 干净的应用服务
class OrderService(
    private val orderFactory: OrderFactory,   // 领域工厂
    private val orderRepository: OrderRepository, // 仓储接口
)
```

**2. `OrderFactory` 依赖 ACL 接口 — ✅ 正确**

`OrderFactory` 作为领域工厂，负责组装一个合法的初始状态的 Order 聚合根。创建订单需要跨上下文查询（商品价格、地址信息），这些外部依赖通过 ACL 接口注入到工厂中，而非注入到聚合根中。

```kotlin
// OrderFactory.kt — 工厂依赖 ACL 接口
class OrderFactoryImpl(
    private val snowFlakSequence: SnowFlakSequence,
    private val goodsService: GoodsService,       // ACL 接口
    private val geoAddressService: GeoAddressService, // ACL 接口
) : OrderFactory
```

这是 DDD 中推荐的做法：
- 工厂是创建复杂聚合的专用组件，允许依赖外部服务来收集创建所需的信息
- ACL 接口定义在 order 上下文内部（`com.jstore.order.acl`），使用上下文本地类型（`GoodsId`, `GoodsInfo`），不泄漏外部模型
- 聚合根 `OrderImpl` 本身不依赖任何外部服务，保持了纯粹性

**3. ACL 接口设计 — ✅ 正确**

ACL 接口定义了上下文本地的数据类型，实现了良好的隔离：

```kotlin
// com.jstore.order.acl.GoodsService — 定义在 order 上下文
data class GoodsId(val spuId: Long, val skuId: Long)  // order 上下文的本地类型
data class GoodsInfo(val id: GoodsId, val version: Long, val price: Price)

interface GoodsService {
    fun queryGoods(goodsId: List<GoodsId>): List<GoodsInfo>
}
```

ACL 实现在 boot 模块（`MockGoodsService`），且不导入 `com.jstore.goods.*` 的任何类型 — 完全隔离。

**4. `GeoAddressService` 返回领域值对象 — ✅ 正确**

`GeoAddressService.getByDistrictCode()` 返回 `GeoAddressInfo`（定义在 `com.jstore.order.domain.order` 包中），这是 order 上下文自己的值对象，不是外部类型。

---

## 1. Cross-Bounded-Context Direct Imports

✅ **未发现违规**

- `j-store-order/src/main/kotlin/` 中无 `com.jstore.goods.*` 导入
- `j-store-goods/src/main/kotlin/` 中无 `com.jstore.order.*` 导入
- `j-store-order-infrastructure/` 中无 `com.jstore.goods.*` 导入
- `j-store-goods-infrastructure/` 中无 `com.jstore.order.*` 导入

两个限界上下文之间的隔离做得很好。

---

## 2. Infrastructure Leaking into Domain

### 🔴 ERROR: 领域工厂使用 Spring `@Component` 注解

- **File**: `j-store-goods/src/main/kotlin/com/jstore/goods/domain/inventory/InventoryFactory.kt`
- **Line**: 5-6
- **Violation**:
  ```kotlin
  @Component
  interface InventoryFactory {
  ```
- **Explanation**: `InventoryFactory` 位于 `domain/inventory/` 包下，属于领域模型层。领域模型文件（实体、值对象、聚合、工厂接口、仓储接口）不应依赖 Spring 框架。`@Component` 是基础设施关注点，不应出现在领域接口上。与之对比，`OrderFactory` 接口就没有任何 Spring 注解。
- **Fix**: 移除 `@Component` 注解，改为在 boot 模块的 `@Configuration` 类中通过 `@Bean` 方法注册：
  ```kotlin
  // InventoryFactory.kt (domain layer) — 移除 @Component
  interface InventoryFactory {
      fun create(createCMD: StorageCreateCMD): Inventory { ... }
  }

  // 在 boot 模块的 Configuration 中注册
  @Bean
  fun inventoryFactory(): InventoryFactory = object : InventoryFactory {}
  ```

> **注意**: `CommodityService` 上的 `@Service` 注解是可接受的，因为它位于 `service/` 包（应用服务层），不在 `domain/` 包中。

---

## 3. DDD Structural Violations

### 🔴 ERROR: `SpuImpl` 聚合根未实现 `AgreeGate`，使用注入的 `DomainEventPublisher` 发布事件

- **File**: `j-store-goods/src/main/kotlin/com/jstore/goods/domain/commodity/Spu.kt`
- **Line**: 40-48
- **Violation**:
  ```kotlin
  interface Spu : Entity<SpuId>  // ← 应该是 AgreeGate<SpuId>

  class SpuImpl(
      override val id: SpuId,
      var status: CommodityStatus,
      val name: String,
      val skus: MutableList<Sku>,
      private val domainEventPublisher: DomainEventPublisher, // ← 注入外部发布器
  ) : Spu
  ```
- **Explanation**: `Spu` 是聚合根，应实现 `AgreeGate<SpuId>` 而非 `Entity<SpuId>`。项目框架提供了 `AgreeGate.publishEvent()` 方法，通过内部事件队列收集事件，在仓储 `save()` 时统一发布。当前实现将 `DomainEventPublisher`（一个基础设施关注点）注入到聚合根构造函数中，违反了聚合根的纯粹性原则。对比 `OrderImpl` 的正确实现：它实现 `AgreeGate<OrderId>`，使用 `domainEventQueue` 和 `publishEvent()` 方法。
- **Fix**:
  ```kotlin
  interface Spu : AgreeGate<SpuId> { ... }

  class SpuImpl(
      override val id: SpuId,
      var status: CommodityStatus,
      val name: String,
      val skus: MutableList<Sku>,
      // 移除 domainEventPublisher 参数
  ) : Spu {
      override val domainEventQueue: Queue<DomainEvent> = LinkedList()

      override fun putOnSale(): Result<Boolean, BusinessError> {
          // ...
          publishEvent(CommodityPublishedEvent(this, this.id))  // 使用 AgreeGate 的方法
          return Success(true)
      }
  }
  ```

### 🔴 ERROR: `SpuImpl` 聚合根暴露可变集合 `MutableList<Sku>`

- **File**: `j-store-goods/src/main/kotlin/com/jstore/goods/domain/commodity/Spu.kt`
- **Line**: 46
- **Violation**:
  ```kotlin
  class SpuImpl(
      ...
      val skus: MutableList<Sku>,  // ← 公开的可变集合
  )
  ```
- **Explanation**: 聚合根的内部集合不应以 `MutableList` 类型暴露给外部。外部代码可以绕过聚合根的 `addSku()` 方法直接修改集合，破坏封装性和业务不变量。对比 `OrderImpl` 的正确做法：使用 `private val _items: MutableList<OrderItem>` + `val items: List<OrderItem> get() = _items.toList()`。
- **Fix**:
  ```kotlin
  class SpuImpl(
      ...
      private val _skus: MutableList<Sku>,
  ) : Spu {
      val skus: List<Sku> get() = _skus.toList()

      override fun addSku(sku: Sku): Result<Boolean, BusinessError> {
          _skus.add(sku)
          return Success(true)
      }
  }
  ```

### 🔴 ERROR: `ReservationRecord` 实体使用 `var status` — 状态变更未封装

- **File**: `j-store-goods/src/main/kotlin/com/jstore/goods/domain/inventory/ReservationRecord.kt`
- **Line**: 13
- **Violation**:
  ```kotlin
  data class ReservationRecord(
      ...
      var status: ReservationStatus,  // ← 可变属性，外部可直接修改
  ) : Entity<ReservationId>
  ```
- **Explanation**: `ReservationRecord` 是一个领域实体，其状态转换（RESERVED → CONFIRMED / RELEASED）是业务规则。当前 `var status` 允许外部代码（如 `InventoryService`）直接赋值 `reservationRecord.status = ReservationStatus.CONFIRMED`，绕过了任何状态转换验证。实体应封装状态变更逻辑。
- **Fix**:
  ```kotlin
  class ReservationRecord(
      override val id: ReservationId,
      val bizCode: String,
      val commodityCode: CommodityCode,
      val amount: BigDecimal,
      private var _status: ReservationStatus,
      val expiryTime: LocalDateTime,
  ) : Entity<ReservationId> {
      val status: ReservationStatus get() = _status

      fun confirm(): Result<Unit, BusinessError> {
          if (_status != ReservationStatus.RESERVED) return Failure(...)
          _status = ReservationStatus.CONFIRMED
          return Success(Unit)
      }

      fun release(): Result<Unit, BusinessError> {
          if (_status != ReservationStatus.RESERVED) return Failure(...)
          _status = ReservationStatus.RELEASED
          return Success(Unit)
      }
  }
  ```

### ⚠️ WARNING: `Inventory`（goods 上下文）未实现 `AgreeGate`

- **File**: `j-store-goods/src/main/kotlin/com/jstore/goods/domain/inventory/Inventory.kt`
- **Line**: 30
- **Violation**: `interface Inventory : Entity<CommodityCode>` — 作为独立聚合根，应实现 `AgreeGate`
- **Explanation**: `Inventory` 有自己的仓储（`InventoryRepository`），是一个独立的聚合根，但它只实现了 `Entity` 而非 `AgreeGate`。这意味着它无法发布领域事件（如库存不足事件、库存预扣成功事件等）。
- **Fix**: 改为 `interface Inventory : AgreeGate<CommodityCode>`

### ⚠️ WARNING: `SpuImpl.status` 使用 `var` 公开暴露

- **File**: `j-store-goods/src/main/kotlin/com/jstore/goods/domain/commodity/Spu.kt`
- **Line**: 43
- **Violation**:
  ```kotlin
  class SpuImpl(
      ...
      var status: CommodityStatus,  // ← 公开的 var
  )
  ```
- **Explanation**: 聚合根的状态应通过业务方法（`putOnSale()`, `tackOffSale()`, `publish()`）来变更，不应允许外部直接赋值。当前 `SpuFactory.update()` 中就直接执行了 `spuImpl.status = old.status`，绕过了业务规则。
- **Fix**: 改为 `private var _status: CommodityStatus`，通过 `val status get() = _status` 暴露只读视图。

---

## 4. Layer Violations

### 🔴 ERROR: `InventoryRepositoryImpl`（goods）使用反射访问领域对象私有字段

- **File**: `j-store-goods-infrastructure/src/main/kotlin/com/jstore/goods/domain/inventory/InventoryRepositoryImpl.kt`
- **Line**: 49-60
- **Violation**:
  ```kotlin
  // 辅助方法：通过反射获取私有字段值
  private fun getAvailableQuantity(inventory: InventoryImpl): java.math.BigDecimal {
      val field = InventoryImpl::class.java.getDeclaredField("availableQuantity")
      field.isAccessible = true
      return field.get(inventory) as java.math.BigDecimal
  }
  ```
- **Explanation**: 基础设施层通过反射破坏领域对象的封装性。这表明领域模型缺少必要的只读访问器供持久化层使用。正确做法是在领域实体上提供只读属性（`val availableQuantity`），或使用专门的快照/memento 模式。
- **Fix**: 在 `InventoryImpl` 中暴露只读属性：
  ```kotlin
  class InventoryImpl(...) : Inventory {
      val availableQuantity: BigDecimal get() = _availableQuantity
      val reservedQuantity: BigDecimal get() = _reservedQuantity
  }
  ```

### ⚠️ WARNING: `OrderController` 返回领域聚合根实现类 `NormalOrderImpl`

- **File**: `j-store-order-boot/src/main/kotlin/com/jstore/order/controller/OrderController.kt`
- **Line**: 22
- **Violation**:
  ```kotlin
  fun create(...): NormalOrderImpl {
      return orderCreateHandler.create(normalOrderCreateCMD)
  }
  ```
- **Explanation**: 控制器直接返回领域聚合根实现类作为 API 响应。这会将领域模型的内部结构暴露给外部客户端，导致 API 与领域模型紧耦合。应使用 DTO/Response 对象。此外，`NormalOrderImpl`、`OrderCreateHandler`、`OrderCancelHandler`、`NormalOrderCreateCMD`、`OrderCancelCMD` 这些类型在当前代码库中找不到定义，说明控制器引用了不存在的类型（可能是旧代码残留）。
- **Fix**: 定义专用的 API 响应 DTO：
  ```kotlin
  data class OrderResponse(val orderId: Long, val status: String, val totalAmount: BigDecimal)

  @PostMapping("/create")
  fun create(@RequestBody cmd: OrderCreateCMD): OrderResponse { ... }
  ```

### ⚠️ WARNING: `OrderController` 引用不存在的类型

- **File**: `j-store-order-boot/src/main/kotlin/com/jstore/order/controller/OrderController.kt`
- **Line**: 4-8
- **Violation**:
  ```kotlin
  import com.jstore.order.domain.order.command.OrderCreateHandler
  import com.jstore.order.domain.order.NormalOrderImpl
  import com.jstore.order.domain.order.command.OrderCancelCMD
  import com.jstore.order.domain.order.command.OrderCancelHandler
  import com.jstore.order.domain.order.command.NormalOrderCreateCMD
  ```
- **Explanation**: 这些类型在当前代码库中均不存在。控制器可能是旧版本残留，与当前的 `OrderService` + `OrderFactory` + `OrderCreateCMD` 架构不匹配。此外，`OrderCreateHandler` 和 `OrderCancelHandler` 被放在 `domain/order/command/` 包中，如果它们是命令处理器（应用服务），不应位于 `command/` 包下。
- **Fix**: 更新控制器以使用当前的 `OrderService`：
  ```kotlin
  @RestController
  @RequestMapping("/order")
  class OrderController(private val orderService: OrderService) {
      @PostMapping("/create")
      fun create(@RequestBody cmd: OrderCreateCMD): OrderResponse {
          val result = orderService.createOrder(cmd)
          return result.map { OrderResponse.from(it) }.getOrThrow()
      }
  }
  ```

---

## 5. ACL Violations

✅ **未发现违规**

- ACL 接口（`GoodsService`, `GeoAddressService`）定义在 `j-store-order/src/main/kotlin/com/jstore/order/acl/`，使用上下文本地类型（`GoodsId`, `GoodsInfo`）
- ACL 实现（`MockGoodsService`, `GeoAddressServiceProxy`）位于 `j-store-order-boot/`，不导入 `com.jstore.goods.*`
- 领域工厂 `OrderFactoryImpl` 通过 ACL 接口访问外部上下文，不直接依赖外部领域类型

> **注意**: `j-store-order-boot/src/main/kotlin/com/jstore/order/acl/Goods.kt` 中的 `Goods` 接口和 `TestController` 看起来是早期的测试/探索代码（微服务间 HTTP 调用），包名也不规范（`com.jstore.com.jstore.order.acl`），建议清理。

---

## 附录：其他观察

### 包名不一致问题

boot 模块中多个文件的 `package` 声明使用了错误的包名 `com.jstore.com.jstore.order.*`（多了一层 `com.jstore`）：

| File | 声明的包名 |
|---|---|
| `OrderController.kt` | `com.jstore.com.jstore.order.controller` |
| `OrderBootConfiguration.kt` | `com.jstore.com.jstore.order.config` |
| `MockGoodsService.kt` | `com.jstore.com.jstore.order.acl.goods` |
| `GeoAddressServiceImpl.kt` | `com.jstore.com.jstore.order.acl.geo.address` |
| `Goods.kt` | `com.jstore.com.jstore.order.acl` |

这虽然不是 DDD 违规，但会导致 Spring 组件扫描和模块依赖出现问题。

### 缺失的领域类型（order 上下文 inventory）

`j-store-order-infrastructure` 中的 `InventoryRepositoryImpl` 和 `InventoryPO` 引用了 `com.jstore.order.domain.inventory.Inventory`、`InventoryId`、`InventoryStatus`、`InventoryRepository` 等类型，但这些类型的源文件在当前代码库中不存在。这些可能是被删除的文件残留，或者是尚未创建的类型。
