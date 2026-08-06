---
kind: error_handling
name: Error Handling — Result Types, BusinessError, and Exception Hierarchy
category: error_handling
scope:
    - '**'
source_files:
    - j-store-common-core/src/main/kotlin/com/jstore/common/errors/BusinessError.kt
    - j-store-common-core/src/main/kotlin/com/jstore/common/errors/Errors.kt
    - j-store-common-core/src/main/kotlin/com/jstore/common/utils/Result.kt
    - j-store-fulfillment-domain/src/main/kotlin/com/jstore/fulfillment/domain/FulfillmentErrors.kt
    - j-store-payment-domain/src/main/kotlin/com/jstore/payment/domain/payment/PaymentErrors.kt
    - j-store-user-domain/src/main/kotlin/com/jstore/user/domain/useraccount/UserAccountErrors.kt
    - j-store-authentication-spring-sdk/src/main/kotlin/com/jstore/authentication/context/AuthenticationException.kt
    - j-store-common-core/src/main/kotlin/com/jstore/common/framework/event/outbox/OutboxSerializationException.kt
    - j-store-order-boot/src/main/kotlin/com/jstore/order/controller/OrderController.kt
---

## What system/approach is used

J-Store uses a **dual-layer error model** built on Kotlin's `Result<T, E>` type:

1. **Business errors** are modeled as immutable `BusinessError` values (message + errorCode + httpCode) returned inside `Result<T, BusinessError>`. This is the primary path for domain and application-layer failures.
2. **Unexpected/runtime exceptions** extend a small hierarchy rooted at `Errors` (a `RuntimeException`) with a companion `CommonErrors` object providing sentinel instances like `INVALID_PARAM`, `ILLEGAL_STATE`, `INTERNAL_ERROR`, `OBJECT_NOT_FOUND`. Domain-specific `*Errors` objects (e.g. `FulfillmentErrors`, `PaymentErrors`, `UserAccountErrors`) define typed sentinel `BusinessError` constants per bounded context.
3. Controllers convert `Result<T, BusinessError>` into HTTP responses via an inline `toResponse` helper that maps `Failure` to `ResponseEntity.status(error.httpCode).body(ErrorResponse(...))`. No global `@ControllerAdvice` is present; error conversion happens locally in each controller.
4. Specialized runtime exceptions (`OutboxSerializationException`, `AuthenticationException`, `LogException`, `ResultUnwrapException`) cover infrastructure concerns.

## Key files and packages

- **j-store-common-core**
  - `com.jstore.common.errors.BusinessError` — immutable error value carrying message, errorCode, httpCode
  - `com.jstore.common.errors.Errors` — base RuntimeException subclass with `msg()`/`cause()` chaining
  - `com.jstore.common.utils.Result` — sealed `Result<T, E>` with `Success`/`Failure`, combinators (`map`, `flatMap`, `fold`, `orElse`, etc.) and factory `Results.ok/err`
  - `com.jstore.common.framework.event.outbox.OutboxSerializationException` — outbox serialization failure
- **Per-domain error registries**
  - `j-store-fulfillment-domain/FulfillmentErrors.kt` — sentinel `BusinessError` constants
  - `j-store-payment-domain/payment/PaymentErrors.kt` — sentinel `BusinessError` constants
  - `j-store-user-domain/useraccount/UserAccountErrors.kt` — sentinel `BusinessError` constants
- **Authentication SDK**
  - `j-store-authentication-spring-sdk/context/AuthenticationException.kt` — unauthenticated user exception
- **Controllers**
  - `j-store-order-boot/order/controller/OrderController.kt` — defines `ErrorResponse` data class and `toResponse` extension that folds `Result<T, BusinessError>` into HTTP responses
  - Similar `ErrorResponse` + `toResponse` patterns appear in other boot modules (`FulfillmentController`, `PaymentController`, `UserAccountController`, `MerchantController`)

## Architecture and conventions

- **Domain layer**: methods return `Result<T, BusinessError>`. Failures are expressed by returning `Failure(BusinessError)` rather than throwing. Domain aggregates expose operations like `activate()`, `deactivate()`, `close()` with this signature.
- **Application layer**: use cases also return `Result<T, BusinessError>`, composing domain calls and propagating business errors upward.
- **Boot/controller layer**: controllers call use cases, then apply `.toResponse { ... }` which `fold`s the result into a `ResponseEntity` with the appropriate HTTP status from `error.httpCode`. The `ErrorResponse` body carries `{ message, errorCode }`.
- **Sentinel constants**: each bounded context owns an `object XxxErrors` exposing pre-defined `BusinessError` instances (e.g. `NOT_FOUND`, `ORDER_CONFLICT`, `INVALID_STATE`). This centralizes error codes and HTTP mappings per domain.
- **Common errors**: `CommonBusinessError` and `CommonErrors` provide shared sentinels (`INVALID_PARAM`, `ILLEGAL_STATE`, `INTERNAL_ERROR`, `OBJECT_NOT_FOUND`) reused across modules.
- **Exception vs Result boundary**: Infrastructure/framework code throws `RuntimeException` subclasses (`OutboxSerializationException`, `AuthenticationException`, `LogException`, `ResultUnwrapException`). Application/domain code avoids exceptions for expected failures, using `Result` instead.
- **No global exception handler**: There is no `@ControllerAdvice` or `GlobalExceptionHandler` found in the codebase. Error-to-HTTP mapping is done inline in each controller via the `toResponse` extension.

## Conventions and constraints

- **Use `Result<T, BusinessError>` for all fallible domain/application operations** — observed consistently across accounting, order, fulfillment, payment, and user modules.
- **Define domain-specific error constants in a `*Errors` object** — every bounded context follows this pattern (Fulfillment, Payment, UserAccount).
- **Attach an HTTP status code to every `BusinessError`** — the `httpCode` field drives the response status directly in controller `toResponse`.
- **Prefer sentinel `BusinessError` values over ad-hoc construction** — domains reference `FulfillmentErrors.NOT_FOUND`, `PaymentErrors.ORDER_CONFLICT`, etc., ensuring consistent error codes.
- **Throw `Errors` (or its subclasses) only for unexpected/runtime conditions** — used in common utilities (JSON parsing, factory creation, logging) where a failure is truly exceptional.
- **Do not swallow `Result.Failure`** — the `fold`/`toResponse` pattern ensures failures are always handled at the boundary.
- **No panics/recover strategy** — Kotlin exceptions are thrown only by infrastructure helpers; there is no `try/catch` recovery logic in business code.