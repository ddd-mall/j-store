# DDD Violation Report — j-store (Goods Modules)

**Generated**: 2025-07-15
**Scanned modules**: `j-store-goods`, `j-store-goods-infrastructure`

## Summary

| Category | ERROR | WARNING | Total |
|---|---|---|---|
| Cross-Context Imports | 0 | 0 | 0 |
| Infrastructure in Domain | 1 | 0 | 1 |
| DDD Structural | 1 | 3 | 4 |
| Layer Violations | 0 | 0 | 0 |
| ACL Violations | 0 | 0 | 0 |
| **Total** | **2** | **3** | **5** |

---

## 1. Cross-Bounded-Context Direct Imports

✅ 未发现违规。`j-store-goods` 和 `j-store-goods-infrastructure` 均未直接导入 `com.jstore.order.*` 包。

---

## 2. Infrastructure Leaking into Domain

### 🔴 ERROR: 领域工厂接口使用了 Spring `@Component` 注解

- **File**: `j-store-goods/src/main/kotlin/com/jstore/goods/domain/inventory/InventoryFactory.kt`
- **Line**: 5
- **Violation**: `@Component interface InventoryFactory`
- **Explanation**: `InventoryFactory` 位于 `domain/inventory/` 包内，属于领域层代码。领域层不应依赖 Spring 框架注解（`org.springframework.stereotype.Component`）。这会导致领域模型与 Spring 容器耦合，违反了领域层的技术无关性原则。
- **Fix**: 移除 `@Component` 注解及 `import org.springframework.stereotype.Component`。将 Bean 注册移至 infrastructure 或 boot 模块的配置类中，通过 `@Bean` 方法注册 `InventoryFactory`。

---

## 3. DDD Structural Violations

### 🔴 ERROR: 聚合根 `SpuImpl` 公开暴露可变状态 `var status`

- **File**: `j-store-goods/src/main/kotlin/com/jstore/goods/domain/commodity/Spu.kt`
- **Line**: 44
- **Violation**: `var status: CommodityStatus`（public 可变属性）
- **Explanation**: `SpuImpl` 是 `Spu` 聚合根的实现类，其 `status` 字段声明为 `var` 且可见性为 public。这意味着外部代码可以直接修改聚合状态（如 `spu.status = CommodityStatus.ON_SALE`），绕过 `putOnSale()`、`tackOffSale()`、`publish()` 等业务方法中的状态校验逻辑。实际上 `SpuFactoryImpl.update()` 中已经出现了 `spuImpl.status = old.status` 这样的直接赋值。聚合根的状态变更必须通过业务方法进行，以保证不变量（invariant）的一致性。
- **Fix**: 将 `var status` 改为 `private var status`（或 `private set`），确保状态只能通过聚合自身的业务方法变更。`SpuFactoryImpl.update()` 中的状态复制应通过构造函数参数传入。

### 🟡 WARNING: 聚合根 `SpuImpl` 使用 `MutableList<Sku>` 暴露内部集合

- **File**: `j-store-goods/src/main/kotlin/com/jstore/goods/domain/commodity/Spu.kt`
- **Line**: 46
- **Violation**: `val skus: MutableList<Sku>`
- **Explanation**: `skus` 虽然声明为 `val`，但类型是 `MutableList`，外部代码可以直接调用 `spu.skus.add(...)` 或 `spu.skus.remove(...)` 来修改集合内容，绕过 `addSku()` 业务方法。这破坏了聚合根对其内部实体集合的封装性。
- **Fix**: 将 `skus` 的类型改为 `List<Sku>`（对外只读），内部使用 `private val _skus: MutableList<Sku>` 持有可变引用。通过 `val skus: List<Sku> get() = _skus.toList()` 暴露不可变视图。

### 🟡 WARNING: 实体 `ReservationRecord` 公开暴露可变状态 `var status`

- **File**: `j-store-goods/src/main/kotlin/com/jstore/goods/domain/inventory/ReservationRecord.kt`
- **Line**: 14
- **Violation**: `var status: ReservationStatus`（public 可变属性）
- **Explanation**: `ReservationRecord` 是一个领域实体（实现了 `Entity<ReservationId>`），其 `status` 字段为 public `var`。当前在 `InventoryService` 中直接通过 `reservationRecord.status = ReservationStatus.CONFIRMED` 修改状态，这属于贫血模型的典型表现——业务逻辑（状态转换及其校验）散落在服务层而非实体内部。
- **Fix**: 将 `var status` 改为 `private set`，在 `ReservationRecord` 中添加 `confirm()` 和 `release()` 等业务方法来封装状态转换逻辑（包括状态校验）。将 `InventoryService` 中的状态判断和赋值逻辑移入实体方法。

### 🟡 WARNING: 聚合根 `SpuImpl` 构造函数注入 `DomainEventPublisher`

- **File**: `j-store-goods/src/main/kotlin/com/jstore/goods/domain/commodity/Spu.kt`
- **Line**: 47
- **Violation**: `private val domainEventPublisher: DomainEventPublisher`（聚合根构造函数参数）
- **Explanation**: 聚合根通过构造函数注入 `DomainEventPublisher` 基础设施服务。这使得聚合根依赖于外部发布机制，增加了构造复杂度（每次创建/重建聚合都需要传入 publisher），也使得聚合难以在纯单元测试中使用。DDD 推荐的做法是聚合根只收集事件，由应用层/仓储层负责发布。
- **Fix**: 在 `SpuImpl` 中维护一个内部事件列表 `private val _domainEvents = mutableListOf<DomainEvent>()`，业务方法中调用 `_domainEvents.add(event)` 收集事件。提供 `fun domainEvents(): List<DomainEvent>` 方法供外部读取。在 `SpuRepository` 的 `save()` 实现中或应用服务中统一发布并清除事件。

---

## 4. Layer Violations

✅ 未发现违规。

- `j-store-goods-infrastructure` 模块的 `src/main/kotlin` 目录下暂无实现文件（commodity 持久化目录为空），因此无法检测 Repository 实现中的业务逻辑泄漏。
- `j-store-goods` 模块无 controller 层代码，不存在 boot 层绕过领域服务的问题。

---

## 5. ACL Violations

✅ 未发现违规。

- `j-store-goods/src/main/kotlin/com/jstore/goods/acl/event/` 下的 ACL 集成事件（`StockReservationRequestedEvent`、`StockConfirmRequestedEvent`、`StockReleaseRequestedEvent`）均使用原始类型（`Long`、`Int`）定义，未泄漏外部上下文的领域类型。
- 事件处理器（`InventoryReservationEventHandler` 等）仅依赖本上下文的领域服务和 ACL 事件，未直接导入外部上下文类型。

---

## 附录：扫描范围

| 模块 | 扫描路径 | 文件数 |
|---|---|---|
| `j-store-goods` | `src/main/kotlin/com/jstore/goods/` | 25 |
| `j-store-goods-infrastructure` | `src/main/kotlin/com/jstore/goods/` | 0（目录为空） |

> 注：`build/`、`src/test/`、`src/main/resources/` 目录已排除。
