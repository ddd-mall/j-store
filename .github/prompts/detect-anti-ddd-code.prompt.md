---
description: "Detect anti-DDD code violations: internal package imports, tight coupling, non-event communication, and module boundary violations in j-store"
argument-hint: "File path or code snippet to analyze"
agent: "agent"
tools: ["search", "codebase"]
---

# Anti-DDD Code Detector

Analyze the provided code for violations of Domain-Driven Design (DDD) and Spring Modulith principles used in j-store. 

## What This Detects

### 1. **Internal Package Violations** ⚠️ CRITICAL
- Direct imports from `internal/` packages across module boundaries
- Pattern: `import com.jstore.{module}.internal.*`
- **Fix**: Use public API from module root instead

### 2. **Tight Coupling - Direct Service Calls** ⚠️ CRITICAL  
- Service-to-service direct calls between modules (e.g., `OrderService` calling `GoodsService`)
- Pattern: `private val goodsService: GoodsService` in another module
- **Fix**: Publish events instead via `ApplicationEventPublisher`

### 3. **Module API Violations** ⚠️ HIGH
- Accessing classes not in module's public API
- Classes should be in module root package, not scattered in implementation packages
- **Fix**: Move to `internal/` or expose through public service/DTO

### 4. **Circular Dependencies** ⚠️ CRITICAL
- Module A depends on Module B which depends on Module A
- **Fix**: Use event-driven communication to break the cycle

### 5. **Missing Event Communication** ⚠️ HIGH
- Cross-module state changes using direct calls
- Pattern: Modifying external module state without events
- **Fix**: Publish events from one module, listen in another with `@ApplicationModuleListener`

### 6. **Mixed Concerns** ⚠️ MEDIUM
- Business logic mixed with infrastructure/UI concerns
- Multiple domains in a single service/class
- **Fix**: Separate by bounded context (module)

## Correct DDD Pattern in j-store

```kotlin
// Order Module (public API)
class OrderService(
    private val events: ApplicationEventPublisher
) {
    fun createOrder(request: CreateOrderRequest): Order {
        // 1. Create order
        val order = Order(...)
        
        // 2. Publish event (NOT direct call!)
        events.publishEvent(OrderCreatedEvent(orderId, goodsId, quantity))
        
        return order
    }
}

// Goods Module (listening to events)
@ApplicationModuleListener
fun onOrderCreated(event: OrderCreatedEvent) {
    reduceStock(event.goodsId, event.quantity)  // ✅ Correct
}
```

## Expected Module Structure

```
com.jstore.{domain}/
├── {DomainEntity}.kt           # Public API
├── {DomainService}.kt          # Public API
├── internal/                   # NOT accessible from other modules
│   ├── {Entity}Repository.kt
│   ├── {Entity}Impl.kt
│   └── Validators.kt
└── events/                     # Domain events (public)
    ├── {Entity}CreatedEvent.kt
    └── {Entity}UpdatedEvent.kt
```

## Analysis Task

For the provided code, identify:
1. **Which DDD violations exist** (use codes above: CRITICAL, HIGH, MEDIUM)
2. **Exact locations** (class, method, line if available)
3. **Why it violates DDD** (explain the principle)
4. **How to fix it** (provide corrected code pattern)

Output format:
- List violations in order of severity
- Group by violation type
- Include code examples for fixes
- Suggest refactoring strategy if multiple violations exist

## When to Use This Prompt

Use when:
- ✅ Reviewing code before commit
- ✅ Refactoring existing services
- ✅ Adding new cross-module communication
- ✅ Planning architecture changes
- ✅ Onboarding team members to DDD practices
