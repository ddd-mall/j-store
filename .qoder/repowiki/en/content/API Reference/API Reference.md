# API Reference

## Current Implemented Routes

| Context | Method and route | Boundary |
| --- | --- | --- |
| Checkout | `POST /api/checkouts` | Authenticated direct-purchase or Cart input; durable HTTP 202 acceptance |
| Checkout | `GET /api/checkouts/{tradeId}` | Buyer-scoped status and optional payment action |
| Cart | `GET /api/carts/current` | Current buyer Cart |
| Cart | `PUT /api/carts/current/items` | Absolute target quantity with expected Cart version |
| Cart | `PUT /api/carts/current/selection` | Replace selected line IDs with expected version |
| Cart | `POST /api/carts/current/refresh` | Refresh assessment with expected version |
| Order | `GET /api/orders`, `GET /api/orders/{orderId}` | Buyer list/detail |
| Order | `POST /api/orders/{orderId}/cancel` | Individual-order cancellation |
| User | `POST /api/users/phone-verifications`, `POST /api/users/register` | Phone-proof registration; anonymous entry points |
| User | `GET /api/users/me` | Authenticated own account |
| User | `PUT /api/users/me/nickname`, `PUT /api/users/me/password` | Own profile/security updates |
| User | `POST /api/users/me/logout` | Revoke current account session |

**Sources** · [Checkout:29-158](file:///Users/101bjb0467/Sources/j-store/backend/j-store-trade-boot/src/main/kotlin/com/jstore/trade/controller/CheckoutController.kt#L29-L158) · [Cart:32-114](file:///Users/101bjb0467/Sources/j-store/backend/j-store-cart-boot/src/main/kotlin/com/jstore/cart/controller/CartController.kt#L32-L114) · [Order:98-150](file:///Users/101bjb0467/Sources/j-store/backend/j-store-order-boot/src/main/kotlin/com/jstore/order/controller/OrderController.kt#L98-L150) · [User:79-154](file:///Users/101bjb0467/Sources/j-store/backend/j-store-user-boot/src/main/kotlin/com/jstore/user/controller/UserAccountController.kt#L79-L154)

## Checkout Contract

The request contains `checkoutRequestId`, `recipient` and either direct `items` or `cartId/expectedCartVersion`. Direct items contain Offer and Catalog references/versions and quantity. Buyer identity comes from `AuthenticatedPrincipal`, including authentication domain; it is not request-body data. Merchant identity is derived from trusted Offer facts.

Creation returns `tradeId`, `status: ACCEPTED`, `statusUrl` and `orderIds`. Query returns persisted status and nullable `payment`; the payment representation includes `paymentId`, status, minor-unit amount, currency, controlled action and expiry. Creation acceptance does not guarantee Orders or payment readiness.

The implemented controller has no public Checkout cancel route. Public `POST /api/orders` is also absent. Do not infer endpoints from design documents or from internal Saga cancellation handlers.

**Sources** · [Checkout DTOs and routes:33-155](file:///Users/101bjb0467/Sources/j-store/backend/j-store-trade-boot/src/main/kotlin/com/jstore/trade/controller/CheckoutController.kt#L33-L155)

## Authentication and Errors

Business controllers use `@RequireLogin` and `@CurrentPrincipal`; explicit anonymous registration routes use `@SkipLogin`. OIDC/Spring Security provides protocol handling and the SDK consumes an `AccessTokenVerifier`. Token acquisition is Authorization Code with PKCE, not old custom User login/refresh endpoints.

Business errors are mapped from `BusinessError.httpCode` and expose message/errorCode. Response shape and status vary by controller; there is no single universal successful response envelope. Buyer-domain/account checks hide unauthorized Order/Trade resources as not found.

**Sources** · [OIDC verifier wiring:188-201](file:///Users/101bjb0467/Sources/j-store/backend/j-store-user-boot/src/main/kotlin/com/jstore/user/oidc/OidcConfiguration.kt#L188-L201) · [Buyer isolation:175-177](file:///Users/101bjb0467/Sources/j-store/docs/domain-modeling.md#L175-L177)

## Scope

After-sale, Payment, Fulfillment, merchant and internal operational controllers remain separate API surfaces. Their older detailed Wiki pages are not an OpenAPI contract or evidence that production providers, fulfillment release or operational authorization have been deployed. Use their current controllers and tests when integrating.
