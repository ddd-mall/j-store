# Inventory Management System

## Dedicated Inventory Context

Inventory is a separate four-layer context with a published read-only ATP API. It is not part of Catalog even though this historical Wiki directory nests it below Goods. Inventory owns the stock mirror and reservations; WMS owns physical stock and Catalog owns product information.

**Sources** · [Inventory registrations:105-113](file:///Users/101bjb0467/Sources/j-store/backend/settings.gradle.kts#L105-L113) · [Ownership:19-24](file:///Users/101bjb0467/Sources/j-store/docs/domain-modeling.md#L19-L24)

## Available to Promise

`StockPosition` contains SKU, fulfillment node, on-hand mirror, reserved quantity, safety stock, isolated quantity, WMS source version and persistence version.

```text
ATP = max(onHand - reserved - safetyStock - isolatedQuantity, 0)
```

Reservation rejects nonpositive quantities and insufficient ATP. Release reduces reserved quantity; confirmation reduces both reserved and on-hand quantities. A WMS update supplies an absolute on-hand value and monotonic source version; repeated or older versions are ignored. Source version and JPA concurrency version are separate.

**Sources** · [StockPosition:25-113](file:///Users/101bjb0467/Sources/j-store/backend/j-store-inventory-domain/src/main/kotlin/com/jstore/inventory/domain/StockPosition.kt#L25-L113)

## Commitments and Queries

A displayed ATP observation is not a stock promise. A persistent `StockReservation` is the promise, related to `tradeId + orderPlanId`. Trade first obtains SaleAuthorization, then requests reservation, then creates Orders through versioned messages. Cart consumes batch ATP observations without reserving stock.

Refund success does not directly restore inventory; physical return receipt/inspection belongs to WMS and results in a new physical-stock fact. Explicit fulfillment-release and provider-capture orchestration remain follow-up boundaries, not a completed production flow.

**Sources** · [Stock commitment:114-137](file:///Users/101bjb0467/Sources/j-store/docs/domain-modeling.md#L114-L137) · [Trade sequence and limits:157-162](file:///Users/101bjb0467/Sources/j-store/docs/domain-modeling.md#L157-L162)
