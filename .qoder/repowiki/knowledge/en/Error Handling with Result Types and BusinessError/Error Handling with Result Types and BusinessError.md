---
kind: error_handling
name: Error Handling with Result Types and BusinessError
category: error_handling
scope:
    - '**'
source_files:
    - j-store-common-core/src/main/kotlin/com/jstore/common/utils/Result.kt
    - j-store-common-core/src/main/kotlin/com/jstore/common/errors/BusinessError.kt
    - j-store-common-core/src/main/kotlin/com/jstore/common/errors/Errors.kt
    - j-store-authentication-spring-sdk/src/main/kotlin/com/jstore/authentication/error/AuthenticationErrors.kt
    - j-store-accounting/src/main/kotlin/com/jstore/accounting/domain/account/AccountingAccountErrors.kt
    - j-store-accounting/src/main/kotlin/com/jstore/accounting/domain/journal/AccountingErrors.kt
    - j-store-boot/src/main/kotlin/com/jstore/order/controller/OrderController.kt
---

The j-store platform implements a layered error handling strategy centered around three core mechanisms: the `Result<T, E>` type for functional error propagation, the `BusinessError` data class for typed business errors, and the `Errors` exception hierarchy for exceptional/unexpected failures. This approach is consistent across all bounded contexts (order, goods, user, accounting) and their infrastructure layers.

**Core Error Types:**
- `Result<T, E>` (`j-store-common-core/src/main/kotlin/com/jstore/common/utils/Result.kt`) — A Rust-inspired sealed class with `Success` and `Failure` variants providing combinators like `map`, `flatMap`, `orElse`, `fold`, `getOrThrow`, and `expect`. It serves as the primary return type for domain methods that can fail, enabling explicit error handling without exceptions.
- `BusinessError` (`j-store-common-core/src/main/kotlin/com/jstore/common/errors/BusinessError.kt`) — A data class carrying `message`, `errorCode`, and `httpCode` fields, used to represent expected business rule violations. Each bounded context defines its own error constants (e.g., `AuthenticationErrors`, `AccountingAccountErrors`, `AccountingErrors`).
- `Errors` (`j-store-common-core/src/main/kotlin/com/jstore/common/errors/Errors.kt`) — An `open class Errors: RuntimeException` hierarchy for unexpected/runtime errors, with `CommonErrors` object providing shared instances like `INVALID_PARAM`, `ILLEGAL_STATE`, `INTERNAL_ERROR`, and `OBJECT_NOT_FOUND`.

**Propagation Pattern:**
Domain services return `Result<T, BusinessError>` rather than throwing exceptions for expected failures. Controllers convert these results to HTTP responses via extension functions like `toResponse()` which map `BusinessError.httpCode` directly to HTTP status codes and wrap the error in an `ErrorResponse` data class containing `message` and `errorCode` fields.

**Context-Specific Errors:**
Each bounded context maintains its own error definitions:
- Authentication SDK: `AuthenticationErrors` with token-related errors (missing, invalid, blacklisted)
- Accounting domain: `AccountingAccountErrors`, `AccountingErrors` for ledger/journal operations
- Common framework: `CommonBusinessError` and `CommonErrors` for cross-cutting concerns

**Exception Strategy:**
The codebase uses exceptions sparingly for truly exceptional conditions:
- `AuthenticationException` for missing authenticated user context
- `LogException` for logging infrastructure failures
- `OutboxSerializationException` for event serialization issues
- `ResultUnwrapException` when `getOrThrow()` is called on a `Failure`

**No Global Exception Handler:**
The search found no `@ExceptionHandler`, `@ControllerAdvice`, or global exception handling middleware. Error handling is done explicitly through `Result` pattern at call sites rather than centralized exception interception.

**Conventions Observed:**
- Domain methods use `Result<T, BusinessError>` return types
- Business errors are defined as objects with descriptive error codes following dot notation (e.g., "Auth.Token.Invalid", "Accounting.Journal.Unbalanced")
- HTTP status codes are embedded in `BusinessError` instances
- Controllers handle `Result` conversion inline rather than through global handlers
- Exceptions are reserved for programming errors and infrastructure failures