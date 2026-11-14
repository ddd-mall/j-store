# User Account API

## Routes

| Method | Route | Input / result |
| --- | --- | --- |
| POST | `/api/users/phone-verifications` | Phone number; returns verification challenge result |
| POST | `/api/users/register` | Phone, nickname, password, challenge ID and verification code; returns registered account |
| GET | `/api/users/me` | Authenticated own account |
| PUT | `/api/users/me/nickname` | New nickname |
| PUT | `/api/users/me/password` | Old password and new password |
| POST | `/api/users/me/logout` | Revoke current principal session |

Only verification and registration explicitly skip login. Account responses expose ID, phone number, nickname, status and timestamps. Business failures expose `message` and `errorCode` using the declared error HTTP code.

**Sources** · [DTOs:34-75](file:///Users/101bjb0467/Sources/j-store/backend/j-store-user-boot/src/main/kotlin/com/jstore/user/controller/UserAccountController.kt#L34-L75) · [Routes:79-154](file:///Users/101bjb0467/Sources/j-store/backend/j-store-user-boot/src/main/kotlin/com/jstore/user/controller/UserAccountController.kt#L79-L154)

## Token Acquisition

Registration does not issue an access/refresh token pair. Local login and WeChat authentication feed Spring Authorization Server, which issues local OIDC tokens via Authorization Code with PKCE S256. Registered clients require proof keys and consent. Refresh grants are configured only for confidential clients with `offline_access`; refresh tokens are rotated rather than reused.

Old custom User login/refresh endpoints and HS256 token-provider implementations are removed. OAuth client/scopes do not grant merchant membership permissions.

**Sources** · [OIDC client settings:111-153](file:///Users/101bjb0467/Sources/j-store/backend/j-store-user-boot/src/main/kotlin/com/jstore/user/oidc/OidcConfiguration.kt#L111-L153) · [Identity boundary:189-201](file:///Users/101bjb0467/Sources/j-store/docs/domain-modeling.md#L189-L201)

## Session and External Identity Boundaries

Logout revokes the authenticated account session. Redis stores issuer-isolated account session/epoch state; PostgreSQL owns OAuth authorization and refresh-family state. WeChat proves an external identity, with explicit verified-phone onboarding/confirmation before account binding. Simulated tests do not establish real WeChat or SMS acceptance; independent review and external integration remain incomplete.

**Sources** · [Logout:149-153](file:///Users/101bjb0467/Sources/j-store/backend/j-store-user-boot/src/main/kotlin/com/jstore/user/controller/UserAccountController.kt#L149-L153) · [Protocol/session semantics:193-201](file:///Users/101bjb0467/Sources/j-store/docs/domain-modeling.md#L193-L201)
