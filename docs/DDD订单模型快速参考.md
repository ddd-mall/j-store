# 🎓 j-store订单模型 DDD 快速参考卡

## 📍 一览表：13个问题与解决方案

### 🔴 Critical (立即修复)

| # | 问题 | 当前状态 | 修复方案 | 优先级 |
|----|------|---------|---------|--------|
| **C1** | Order可变性 | items/status/amount都是var | 改为val，items返回副本 | 🔴🔴🔴 |
| **C2** | OrderItem模糊 | var字段+id混乱 | 确认为聚合内实体 | 🔴🔴🔴 |
| **C3** | 业务逻辑分散 | 在应用层(事务脚本) | 移到Order中(reserve/pay等) | 🔴🔴🔴 |
| **C4** | 缺乏领域事件 | 没有事件发布机制 | 创建OrderEvent体系 | 🔴🔴🔴 |

### 🟠 High (短期修复)

| # | 问题 | 当前状态 | 修复方案 | 优先级 |
|----|------|---------|---------|--------|
| **H1** | Repository混淆 | 暴露基础设施概念(Lock) | 清晰界面(add/save/find) | 🟠🟠 |
| **H2** | 跨模块耦合 | 直接依赖GoodsService | 反腐层+事件驱动 | 🟠🟠 |
| **H3** | 值对象可变 | UserInfo/GeoAddress是var | 改为val + 加验证 | 🟠🟠 |
| **H4** | 并发无控制 | version字段未使用 | 乐观锁+异常处理 | 🟠🟠 |

### 🟡 Medium (中期修复)

| # | 问题 | 当前状态 | 修复方案 | 优先级 |
|----|------|---------|---------|--------|
| **M1** | 事务边界不清 | - | 标注每个应用服务 | 🟡 |
| **M2** | 业务流程文档缺 | - | 状态机+测试文档 | 🟡 |
| **M3** | OrderId验证不足 | 任意String | 长度+格式验证 | 🟡 |
| **M4** | Quantity等缺失 | 直接用int/BigDecimal | 创建值对象 | 🟡 |

### 🟢 Low (后续修复)

| # | 问题 | 当前状态 | 修复方案 | 优先级 |
|----|------|---------|---------|--------|
| **L1** | 代码组织结构 | - | 分层目录结构 | 🟢 |

---

## 🎯 关键改进模式

### 模式1：聚合根不可变性

```kotlin
// ❌ 之前
data class Order(
    val items: List<OrderItem>,  // 可变集合
    var status: OrderStatus      // var可变
)

// ✅ 之后
class Order {
    private val _items: MutableList<OrderItem>
    val items: List<OrderItem> get() = _items.toList()  // 只读副本
    val status: OrderStatus  // val不可变
    
    fun reserve(): Result<List<DomainEvent>> { ... }
}
```

### 模式2：业务方法返回事件

```kotlin
// ❌ 之前
fun reserve(): Order {
    status = OrderStatus.RESERVED
    return this
}

// ✅ 之后
fun reserve(): Result<List<DomainEvent>> {
    check(status == OrderStatus.INIT)
    return Result.success(listOf(OrderReservedEvent(id)))
}
```

### 模式3：反腐层隔离

```kotlin
// ❌ 之前
class OrderService(private val goodsService: GoodsService)

// ✅ 之后
// 订单上下文定义自己的接口
interface GoodsAdapter {
    fun checkGoodsExists(goodsId: String): Boolean
}

// 基础设施实现（隐藏GoodsService）
class GoodsAdapterImpl(private val goodsService: GoodsService) : GoodsAdapter
```

### 模式4：值对象验证

```kotlin
// ❌ 之前
data class UserInfo(
    var phoneNumber: PhoneNumber?  // var+nullable
)

// ✅ 之后
data class UserInfo(
    val phoneNumber: PhoneNumber   // val+非空
) {
    init {
        require(phoneNumber.value.matches(Regex(...)))
    }
}
```

---

## 📊 改进优先级矩阵

```
高影响
  ↑    ┌─────────────────────────┐
  │    │ C1,C2,C3,C4            │ ← 立即做（本周）
  │    │ (聚合根,事件)          │
  │    ├─────────────────────────┤
  │    │ H1,H2,H3,H4            │ ← 短期做（1-2周）
  │    │ (Repository,反腐,并发) │
  │    ├─────────────────────────┤
  │    │ M1,M2,M3,M4            │ ← 中期做（2-3周）
  │    │ (文档,值对象)          │
  │    └─────────────────────────┘
  └────────────────────────────────→ 低影响
   低工作量          高工作量
```

---

## 💻 代码框架对照

### 当前→改进 对照表

| 层级 | 当前实现 | 改进后实现 | 位置 |
|------|---------|-----------|------|
| 聚合根 | 贫血Order | 富Order+事件 | domain/model/Order.kt |
| 值对象 | 可变fields | 不可变+验证 | domain/model/ValueObjects.kt |
| 仓储 | 混淆概念 | 清晰接口 | domain/repository/OrderRepository.kt |
| 应用 | 事务脚本 | 编排者 | application/OrderApplicationService.kt |
| 反腐 | 直接依赖 | 适配器 | infrastructure/client/GoodsAdapter.kt |

---

## ⏱️ 实施时间估算

```
Phase 1: 聚合根重构
├─ Order不可变性       4h
├─ OrderItem整理       2h
├─ 业务方法实现       4h
└─ 小计              10h

Phase 2: 事件与隔离
├─ 领域事件定义       3h
├─ 事件发布集成       3h
├─ 反腐层实现        4h
└─ 小计             10h

Phase 3: 值对象与仓储
├─ 值对象完整性       4h
├─ Repository清晰     2h
├─ 并发控制          2h
└─ 小计              8h

Phase 4: 文档与测试
├─ 单元测试         6h
├─ 集成测试         4h
├─ 文档编写         2h
└─ 小计            12h

总计: 约48小时（2周 × 3人天）
```

---

## 🧪 测试检查清单

```kotlin
// 聚合根测试
[ ] Order.reserve() - 检查状态转移
[ ] Order.pay() - 检查前置条件
[ ] Order.items - 检查只读视图
[ ] Order.getDomainEvents() - 检查事件记录

// 值对象测试
[ ] Price验证 - 负数/精度
[ ] PhoneNumber验证 - 格式
[ ] GeoAddressInfo验证 - 坐标范围
[ ] Quantity验证 - 数量范围

// 应用服务测试
[ ] createOrder - 检查事件发布
[ ] reserveOrder - 检查反腐层调用
[ ] payOrder - 检查并发异常处理

// 集成测试
[ ] 端到端订单流程
[ ] 事件监听触发
[ ] 并发修改场景
```

---

## 📌 关键点提示

### ✅ DDD最佳实践

1. **聚合根即自治单位**
   - 聚合根内的对象不能被外部直接修改
   - 所有修改通过聚合根方法
   - 返回不可变的视图

2. **领域事件是一等公民**
   - 发生重要业务事件时立即记录
   - 事件名称使用过去式 (Ordered, Reserved, Paid)
   - 事件包含重要的业务上下文

3. **反腐层保护边界**
   - 订单模块定义自己的接口
   - 不直接依赖Goods/Payment模块
   - 隐藏外部系统的复杂性

4. **值对象是类型安全的**
   - 用Money代替BigDecimal
   - 用Quantity代替Int
   - 用OrderId代替String

### ❌ 常见陷阱

1. **不要把Repository当ORM**
   - Repository是领域层的，不是数据库接口
   - findByIdAndLock()这样的方法不应该出现

2. **不要让聚合根暴露可变集合**
   - 返回List副本，不是可变的集合引用
   - 否则外部代码能绕过业务规则

3. **不要在值对象中存储引用**
   - 值对象不应该有依赖注入
   - 不应该调用外部服务

4. **不要在领域模型中混入ORM注解**
   - 领域模型应该独立于框架
   - ORM实体应该单独存在

---

## 🔗 关键文件映射

```
改进前                          改进后
├── Order.kt                  ├── domain/
│  (贫血，无业务)               ├─ model/
├── OrderItem.kt              │  ├─ Order.kt (富模型)
│  (混淆的类型)                 │  ├─ OrderItem.kt
├── OrderStatus.kt            │  ├─ ValueObjects.kt
├── OrderRepository.kt        │  └─ OrderStatusMachine.kt
│  (混淆概念)                   ├─ event/
├── OrderApplicationService   │  └─ OrderDomainEvents.kt
│  (事务脚本)                   ├─ service/
├── OrderRepositoryImpl.kt     │  └─ OrderStatusTransitionService.kt
│  (混合关注点)                 ├─ repository/
│                             │  └─ OrderRepository.kt
└── *.kt files                └── application/
   (需要重构)                    ├─ OrderApplicationService.kt
                              └── infrastructure/
                                 ├─ persistence/
                                 │  └─ OrderRepositoryImpl.kt
                                 └─ client/
                                    └─ GoodsAdapterImpl.kt
```

---

## 📖 学习资源推荐

- **书籍**: 《Domain-Driven Design》(Blue Book)
- **参考**: Spring Data JPA + Domain Events
- **模式**: 聚合根 + 值对象 + 领域事件
- **架构**: Spring Modulith 最佳实践

---

## 🎬 下一步行动

**本周目标**:
1. [ ] 完成Order不可变性改造
2. [ ] OrderItem类型确认
3. [ ] 添加基本业务方法

**下周目标**:
1. [ ] 完成领域事件框架
2. [ ] 实现反腐层
3. [ ] 编写单元测试

**第三周目标**:
1. [ ] 并发控制实现
2. [ ] 集成测试
3. [ ] 性能优化

---

## 📞 常见Q&A

**Q: Order为什么必须是不可变的？**
A: 确保业务规则的一致性。如果items被外部代码直接修改，Order无法验证"订单必须有行项"等规则。

**Q: OrderItem应该是值对象吗？**
A: 否。因为需要用lineId区分（即使内容相同），这是实体的特征。

**Q: 领域事件什么时候发布？**
A: 在应用服务中，聚合根保存到数据库之后。这样确保原子性。

**Q: 反腐层增加了代码复杂性吗？**
A: 短期是的。但长期看，它隔离了变化，使模块独立演进。

**Q: 需要使用事件溯源吗？**
A: 不一定。但使用领域事件使事件溯源成为可选的。


