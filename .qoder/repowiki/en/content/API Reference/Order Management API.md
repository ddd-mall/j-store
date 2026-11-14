# Order Management API

## Implemented Buyer Endpoints

All routes belong to `OrderController`, require login and derive authentication domain/account from `AuthenticatedPrincipal`.

| Method | Route | Request |
| --- | --- | --- |
| GET | `/api/orders/{orderId}` | Path order ID |
| GET | `/api/orders` | `page` defaults to 1; `size` defaults to 10 |
| POST | `/api/orders/{orderId}/cancel` | `category`, `description` |

Public Order creation is not implemented. Submit a purchase using `POST /api/checkouts`; Trade internally coordinates resource commitments and Order creation through versioned messages.

**Sources** · [Order routes:98-150](file:///Users/101bjb0467/Sources/j-store/backend/j-store-order-boot/src/main/kotlin/com/jstore/order/controller/OrderController.kt#L98-L150) · [Checkout creation:80-97](file:///Users/101bjb0467/Sources/j-store/backend/j-store-trade-boot/src/main/kotlin/com/jstore/trade/controller/CheckoutController.kt#L80-L97) · [Internal creation boundary:157-162](file:///Users/101bjb0467/Sources/j-store/docs/domain-modeling.md#L157-L162)

## Response Fields

Order responses include ID, merchant and buyer IDs; trade/payment/fulfillment/commitment dimensions; currency and amount breakdown; item snapshots; creation/update timestamps. Items include Offer/store/version, fulfillment node, channel, SPU/SKU, display snapshots, quantity, unit price and refund totals. Buyer nickname and verified phone are frozen internally, not exposed as public Order DTO fields.

List response contains `current`, `size` and `records`. The current controller maps `size` from `Page.totalElements`, not directly from the requested page size; consumers must follow the actual contract rather than assuming a conventional page-size field.

**Sources** · [DTOs:46-94](file:///Users/101bjb0467/Sources/j-store/backend/j-store-order-boot/src/main/kotlin/com/jstore/order/controller/OrderController.kt#L46-L94) · [Pagination mapping:114-133](file:///Users/101bjb0467/Sources/j-store/backend/j-store-order-boot/src/main/kotlin/com/jstore/order/controller/OrderController.kt#L114-L133)

## Authorization and Errors

Detail, list and cancellation pass both authentication domain and account ID to the use case. Cross-buyer access is treated as not found. Business failures use their declared HTTP code with `{message, errorCode}`; successful cancellation maps to an empty use-case result rather than returning a newly created Order.

**Sources** · [Principal propagation:98-150](file:///Users/101bjb0467/Sources/j-store/backend/j-store-order-boot/src/main/kotlin/com/jstore/order/controller/OrderController.kt#L98-L150) · [Error mapping:196-203](file:///Users/101bjb0467/Sources/j-store/backend/j-store-order-boot/src/main/kotlin/com/jstore/order/controller/OrderController.kt#L196-L203)
