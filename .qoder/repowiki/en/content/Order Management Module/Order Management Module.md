# Order Management Module

## Responsibility

Order owns an individual confirmed transaction record, buyer lifecycle, historical transaction snapshots and after-sale rules. Trade owns Checkout idempotency, sales authorization, stock commitments, order planning and settlement coordination. Order is not the purchase entry point or Checkout Process Manager.

Order exposes domain/application/infrastructure/boot layers and `j-store-order-api` for Accounting's scalar bookkeeping snapshot queries.

**Sources** · [Modules:35-43](file:///Users/101bjb0467/Sources/j-store/backend/settings.gradle.kts#L35-L43) · [Published query contracts:62](file:///Users/101bjb0467/Sources/j-store/docs/domain-modeling.md#L62-L62)

## Frozen Facts

Order items retain Catalog snapshot version, SPU/SKU, Offer/store/version/channel, price/quantity, fulfillment node and display text. The root freezes buyer authentication domain, domain-local account ID, nickname and verified phone. Recipient contact information is a distinct fulfillment instruction. History is not reconstructed from mutable product/user records.

**Sources** · [Order snapshots and privacy:166-177](file:///Users/101bjb0467/Sources/j-store/docs/domain-modeling.md#L166-L177)

## Creation and Buyer Operations

Trade persists its independent identity and plans, obtains authorization/reservation and emits versioned Order creation commands. Orders return created/rejected facts; Trade waits for every plan and balanced totals before preparing the SettlementPlan. Order does not retain SaleAuthorization/StockReservation identifiers or call inventory services as its own Saga.

The public controller requires login and exposes list, detail and individual-order cancellation. Public Order creation has been removed; Checkout creation/query is in Trade boot. Checkout cancellation is not currently exposed by its controller/use-case contract.

**Sources** · [Creation protocol:143-162](file:///Users/101bjb0467/Sources/j-store/docs/domain-modeling.md#L143-L162) · [Order endpoints:98-150](file:///Users/101bjb0467/Sources/j-store/backend/j-store-order-boot/src/main/kotlin/com/jstore/order/controller/OrderController.kt#L98-L150) · [Checkout contract:122-130](file:///Users/101bjb0467/Sources/j-store/backend/j-store-trade-application/src/main/kotlin/com/jstore/trade/service/CheckoutApplicationService.kt#L122-L130)

## Authorization and Status

Buyer operations pass both authentication domain and account ID. Unauthorized resources are hidden as not found. HTTP responses expose separate trade/payment/fulfillment/commitment dimensions and amounts, not a single all-purpose state or frozen buyer nickname/phone. Internal state enum values alone do not prove that capture, cancellation or fulfillment workflows are fully delivered.

**Sources** · [Public DTO:46-83](file:///Users/101bjb0467/Sources/j-store/backend/j-store-order-boot/src/main/kotlin/com/jstore/order/controller/OrderController.kt#L46-L83) · [Buyer isolation:175-177](file:///Users/101bjb0467/Sources/j-store/docs/domain-modeling.md#L175-L177)
