# Spring Modulith 快速入门指南

## 3 分钟理解 Spring Modulith

### 核心问题：单体应用的模块边界难以维护

**传统单体应用的痛点：**
```kotlin
// 订单服务直接调用商品服务 ❌
class OrderService(
    private val goodsService: GoodsService  // 紧耦合
) {
    fun createOrder() {
        goodsService.updateStock()  // 直接调用
    }
}
```

**问题：**
- 模块边界模糊
- 容易产生循环依赖
- 难以重构和测试
- 代码逐渐腐化

### Spring Modulith 的解决方案

**1. 通过包结构定义模块边界**
```
com.jstore/
├── order/              ← 订单模块
│   ├── Order.kt        ← 公共 API（可访问）
│   └── internal/       ← 内部实现（不可访问）
│       └── OrderRepository.kt
└── goods/              ← 商品模块
    ├── Goods.kt
    └── internal/
        └── GoodsRepository.kt
```

**2. 通过事件解耦模块**
```kotlin
// 订单服务发布事件 ✅
class OrderService(
    private val events: ApplicationEventPublisher
) {
    fun createOrder() {
        events.publishEvent(OrderCreatedEvent(...))  // 发布事件
    }
}

// 商品服务监听事件 ✅
@ApplicationModuleListener
fun onOrderCreated(event: OrderCreatedEvent) {
    updateStock(event.goodsId, event.quantity)  // 异步处理
}
```

**3. 自动验证架构规则**
```kotlin
@Test
fun `验证模块结构`() {
    ApplicationModules.of(App::class.java).verify()  // 自动检查
}
```

## 5 分钟上手

### 步骤 1：添加依赖

```kotlin
// build.gradle.kts
dependencies {
    implementation(platform("org.springframework.modulith:spring-modulith-bom:1.3.3"))
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
}
```

### 步骤 2：组织包结构

```
src/main/kotlin/com/jstore/
├── order/                          # 订单模块
│   ├── OrderService.kt             # 公共 API
│   ├── events/
│   │   └── OrderCreatedEvent.kt    # 事件
│   └── internal/
│       └── OrderServiceImpl.kt     # 内部实现
└── goods/                          # 商品模块
    ├── GoodsService.kt
    └── internal/
        └── InventoryListener.kt
```

### 步骤 3：定义事件

```kotlin
// order/events/OrderCreatedEvent.kt
data class OrderCreatedEvent(
    val orderId: String,
    val goodsId: String,
    val quantity: Int
)
```

### 步骤 4：发布事件

```kotlin
// order/internal/OrderServiceImpl.kt
@Service
internal class OrderServiceImpl(
    private val events: ApplicationEventPublisher
) : OrderService {
    
    @Transactional
    override fun createOrder(request: CreateOrderRequest): Order {
        val order = saveOrder(request)
        
        // 发布事件
        events.publishEvent(OrderCreatedEvent(
            orderId = order.id,
            goodsId = request.goodsId,
            quantity = request.quantity
        ))
        
        return order
    }
}
```

### 步骤 5：监听事件

```kotlin
// goods/internal/InventoryListener.kt
@Component
internal class InventoryListener(
    private val inventoryService: InventoryService
) {
    @ApplicationModuleListener  // 使用 Spring Modulith 的监听器
    fun onOrderCreated(event: OrderCreatedEvent) {
        inventoryService.reduceStock(event.goodsId, event.quantity)
    }
}
```

### 步骤 6：验证架构

```kotlin
// test/kotlin/ModularityTest.kt
@Test
fun `验证模块结构`() {
    ApplicationModules.of(JStoreApplication::class.java).verify()
}
```

## 与传统方式对比

| 方面 | 传统方式 | Spring Modulith |
|------|---------|-----------------|
| 模块通信 | 直接调用服务 | 事件驱动 |
| 耦合度 | 紧耦合 | 松耦合 |
| 边界保护 | 靠自觉 | 自动验证 |
| 事务管理 | 同步事务 | 可异步 + 事件持久化 |
| 可测试性 | 需要 Mock 多个依赖 | 只需测试事件 |
| 重构难度 | 高（牵一发动全身） | 低（模块独立） |
| 文档 | 手工维护 | 自动生成 |

## 核心优势

### 1. 架构可视化
```bash
# 自动生成模块依赖图
gradlew test  # ModularityTest 会生成文档
# 查看：target/spring-modulith-docs/components.puml
```

### 2. 编译期检查
```kotlin
// ❌ 编译不通过：不能访问 internal 包
import com.jstore.goods.internal.GoodsRepository  // 错误！
```

### 3. 事件可靠性
```yaml
# 启用事件持久化
spring.modulith.events.jdbc.enabled: true
```
- 事件自动持久化到数据库
- 失败自动重试
- 应用重启后继续处理未完成的事件

### 4. 渐进式演进
```
单体应用 
  ↓ 
模块化单体（Spring Modulith）
  ↓ 
微服务（必要时）
```

## 常见问题

### Q1：与微服务有什么区别？

| 特性 | Spring Modulith | 微服务 |
|------|-----------------|--------|
| 部署 | 单一进程 | 多个进程 |
| 通信 | 进程内事件 | HTTP/gRPC/消息队列 |
| 数据库 | 共享数据库 | 各自独立 |
| 复杂度 | 低 | 高 |
| 扩展性 | 整体扩展 | 独立扩展 |

**选择建议：**
- 团队 < 20 人 → Spring Modulith
- 团队 > 50 人 → 微服务
- 20-50 人 → 看业务复杂度

### Q2：必须使用事件吗？

不是必须的，但强烈推荐。

**可以混合使用：**
```kotlin
// 同步调用（适合查询）
val goods = goodsService.getGoods(id)

// 异步事件（适合命令）
events.publishEvent(OrderCreatedEvent(...))
```

### Q3：事件会丢失吗？

不会，如果启用了事件持久化：
```yaml
spring.modulith.events.jdbc.enabled: true
```

Spring Modulith 会：
1. 先保存事件到数据库
2. 再发布事件
3. 处理成功后标记完成
4. 失败会自动重试

### Q4：性能如何？

**进程内事件**非常快：
- 无网络开销
- 无序列化开销
- 可以在同一个事务中（如果需要）

**对比：**
- 方法调用：~1 微秒
- 进程内事件：~10 微秒
- HTTP 调用：~1-10 毫秒

### Q5：如何处理事件顺序？

Spring Modulith 保证同一类型的事件按发布顺序处理。

```kotlin
@ApplicationModuleListener
@Order(1)  // 优先级
fun handler1(event: OrderCreatedEvent) { ... }

@ApplicationModuleListener
@Order(2)
fun handler2(event: OrderCreatedEvent) { ... }
```

## 最佳实践

### ✅ DO

1. **按业务能力划分模块**
   ```
   com.jstore.order    # 订单域
   com.jstore.goods    # 商品域
   com.jstore.payment  # 支付域
   ```

2. **使用 internal 包隔离实现**
   ```kotlin
   com.jstore.order/
   ├── Order.kt              # 公共
   ├── OrderService.kt       # 公共
   └── internal/             # 私有
       └── OrderRepository.kt
   ```

3. **事件命名使用过去式**
   ```kotlin
   OrderCreatedEvent      ✅
   CreateOrderEvent       ❌
   ```

4. **定期运行架构测试**
   ```kotlin
   @Test
   fun verifyModules() {
       modules.verify()
   }
   ```

### ❌ DON'T

1. **不要按技术层次划分模块**
   ```
   com.jstore.controller   ❌
   com.jstore.service      ❌
   com.jstore.repository   ❌
   ```

2. **不要在事件中传递实体**
   ```kotlin
   // ❌ 不要这样
   data class OrderCreatedEvent(val order: OrderEntity)
   
   // ✅ 应该这样
   data class OrderCreatedEvent(val orderId: String, ...)
   ```

3. **不要创建过大的模块**
   - 单个模块建议 < 20 个类
   - 太大就拆分

4. **不要跨模块直接访问数据库**
   ```kotlin
   // ❌ 不要在 order 模块中直接查询 goods 表
   jdbcTemplate.query("SELECT * FROM goods ...")
   ```

## 实战建议

### 适合您的 j-store 项目

**当前结构：**
```
j-store/
├── j-store-order/         # 订单模块
├── j-store-goods/         # 商品模块
└── j-store-application/   # 主应用
```

**改造方案：**

1. **保持现有模块结构**（不需要大改）
2. **在各模块中添加 events 包**
3. **使用事件代替直接服务调用**
4. **在 j-store-application 中添加架构测试**

**改造成本：** 低
**收益：** 高（更清晰的边界，更好的可维护性）

## 下一步

1. ✅ 阅读完整指南：`Spring-Modulith完全指南.md`
2. ✅ 查看示例代码：`Spring-Modulith示例代码.kt`
3. ⬜ 在项目中添加依赖
4. ⬜ 编写第一个架构测试
5. ⬜ 重构一个功能使用事件驱动

## 参考资源

- 官方文档：https://spring.io/projects/spring-modulith
- 官方示例：https://github.com/spring-projects/spring-modulith
- 最佳实践：https://www.baeldung.com/spring-modulith

---

**总结：Spring Modulith = 单体的简单性 + 微服务的模块化 + 自动化的架构守护**

