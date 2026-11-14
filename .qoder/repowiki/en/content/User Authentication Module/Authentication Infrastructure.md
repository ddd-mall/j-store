# Authentication Infrastructure

## Protocol Persistence and Signing

Spring Authorization Server stores registered clients and consent through JDBC. `DigestAuthorizationService` owns authorization and token digest persistence, while `OidcSessionGuard` connects account/session checks to protocol and access-token verification. RSA signing keys are loaded from configured resources; the decoder selects RS256 and validates issuer/time. The removed HS256 `JwtTokenProvider` is not a runtime component.

**Sources** · [Signing:69-104](file:///Users/101bjb0467/Sources/j-store/backend/j-store-user-boot/src/main/kotlin/com/jstore/user/oidc/OidcConfiguration.kt#L69-L104) · [Persistence and verifier:111-201](file:///Users/101bjb0467/Sources/j-store/backend/j-store-user-boot/src/main/kotlin/com/jstore/user/oidc/OidcConfiguration.kt#L111-L201)

## Redis Account Session Store

`RedisAccountSessionStore` implements account and SDK session contracts. Keys use a supplied namespace plus account ID/session ID. Epoch creation uses a random positive seed; revocation advances the epoch. Lua atomically compares the current epoch before creating or validating a session. A missing old epoch/session cannot silently validate an old token.

- `createSession` requires positive TTL and a matching epoch.
- `revokeSession` deletes the session key.
- `revokeAllSessions` advances the account epoch.
- `isSessionActive` requires both matching epoch and matching session state.

Redis is not the refresh-token protocol store; PostgreSQL authorization digests own that responsibility. Availability failures must not be bypassed by accepting JWT signature alone.

**Sources** · [Redis operations and scripts:24-107](file:///Users/101bjb0467/Sources/j-store/backend/j-store-user-infrastructure/src/main/kotlin/com/jstore/user/domain/useraccount/RedisAccountSessionStore.kt#L24-L107) · [Protocol separation:197-200](file:///Users/101bjb0467/Sources/j-store/docs/domain-modeling.md#L197-L200)

## Boundary and Validation

Password credential verification and session creation coordinate with account row locking. Local OIDC identity is `authenticationDomain + accountId`; external WeChat IDs and OAuth scopes are not merchant permissions. Real provider/SMS behavior and production readiness require separate external validation and independent review.

**Sources** · [Identity/session constraints:189-201](file:///Users/101bjb0467/Sources/j-store/docs/domain-modeling.md#L189-L201)
