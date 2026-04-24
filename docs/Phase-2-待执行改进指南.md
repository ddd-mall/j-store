# 📋 Phase 2: 反腐层与应用服务改进指南

> 继续优化模块隔离和并发控制问题

## 🎯 Phase 2目标

这个阶段将完成以下改进:

- [ ] 创建反腐层接口（隔离Goods、Payment等模块）
- [ ] 改进应用服务（编排者模式）
- [ ] 实现Repository具体类
- [ ] 添加事件发布机制

---

## 📝 待执行的改进清单

### 需要创建的文件

**反腐层接口**:
- [ ] `domain/service/GoodsAdapter.kt` - 商品服务反腐层
- [ ] `domain/service/PaymentAdapter.kt` - 支付服务反腐层
- [ ] `domain/service/LogisticsAdapter.kt` - 物流服务反腐层

**应用服务**:
- [ ] `application/OrderApplicationService.kt` - 订单应用服务
- [ ] `application/command/CreateOrderCommand.kt` - 创建订单命令

**基础设施实现**:
- [ ] `infrastructure/persistence/OrderRepositoryImpl.kt` - Repository实现
- [ ] `infrastructure/persistence/OrderEntity.kt` - ORM实体

### 需要改进的现有文件

- [ ] `OrderRepository.kt` - 添加默认实现（如果需要）
- [ ] 检查现有的Repository实现

---

## 🏗️ Phase 2详细改进步骤

### Step 1: 创建反腐层接口

**GoodsAdapter.kt** 应该定义:
```kotlin
interface GoodsAdapter {
    fun checkGoodsAvailable(goodsId: String, quantity: Int): GoodsAvailabilityResult
    fun getGoodsPrice(goodsId: String): Result<Price>
    fun reserveStock(goodsId: String, quantity: Int): Result<StockReservation>
    fun releaseStock(goodsId: String, quantity: Int): Result<Unit>
}
```

**PaymentAdapter.kt** 应该定义:
```kotlin
interface PaymentAdapter {
    fun initiatePayment(orderId: OrderId, amount: Price): Result<PaymentId>
    fun refundPayment(paymentId: PaymentId, amount: Price): Result<Unit>
}
```

### Step 2: 创建应用服务

**OrderApplicationService.kt** 应该:
- 使用反腐层而不是直接依赖
- 发布领域事件
- 处理并发异常

```kotlin
@Service
@Transactional
class OrderApplicationService(
    private val orderRepository: OrderRepository,
    private val goodsAdapter: GoodsAdapter,
    private val paymentAdapter: PaymentAdapter,
    private val eventPublisher: ApplicationEventPublisher
) {
    
    fun createOrder(command: CreateOrderCommand): Result<OrderId> {
        val order = OrderImpl.Factory.create(
            id = OrderId(command.orderId),
            buyerInfo = command.buyerInfo,
            items = command.items,
            shippingAddressInfo = command.shippingAddressInfo
        )
        
        orderRepository.add(order)
        
        // 发布事件
        publishDomainEvents(order)
        
        return Result.success(order.id)
    }
    
    fun reserveOrder(orderId: OrderId): Result<Unit> {
        val order = orderRepository.findById(orderId) 
            ?: return Result.failure(Exception("订单不存在"))
        
        order.reserve()
        
        orderRepository.save(order)
        publishDomainEvents(order)
        
        return Result.success(Unit)
    }
    
    private fun publishDomainEvents(order: OrderImpl) {
        if (order is OrderImpl) {
            order.getDomainEvents().forEach { event ->
                eventPublisher.publishEvent(event)
            }
            order.clearDomainEvents()
        }
    }
}
```

### Step 3: 实现Repository

**OrderRepositoryImpl.kt** 应该:
- 实现add()和save()方法
- 处理ORM映射
- 处理并发异常

### Step 4: 添加事件监听器

在其他模块中监听Order事件，进行相应的操作。

---

## 🔍 预期改进成果

完成Phase 2后:

```
改进前: 5/10 🟡
改进后: 7/10 🟢

提升: +2/10
```

### 改进的维度

- ✅ **模块隔离**: 4/10 → 8/10（反腐层完整）
- ✅ **应用服务**: 3/10 → 7/10（编排者模式）
- ✅ **事件驱动**: 6/10 → 8/10（完整发布机制）

---

## 📊 预计工作量

| 任务 | 估算时间 |
|------|---------|
| 创建反腐层接口 | 1.5小时 |
| 创建应用服务 | 1.5小时 |
| 实现Repository | 1小时 |
| 测试和调试 | 1小时 |
| **小计** | **5小时** |

---

## ✅ Phase 2完成检查清单

- [ ] 反腐层接口已创建
- [ ] 应用服务已实现
- [ ] Repository实现已完成
- [ ] 事件发布机制已集成
- [ ] 代码编译无误
- [ ] 基本集成测试通过

---

## 📞 需要帮助?

如果您需要进行Phase 2的改进，请告诉我，我会立即帮您实施！

可以执行的命令:
- `帮我实现Phase 2的反腐层`
- `帮我创建应用服务`
- `帮我实现Repository`
- `帮我添加测试用例`

---

**下一步**: 完成Phase 2 → Phase 3 (并发控制和完整测试)


