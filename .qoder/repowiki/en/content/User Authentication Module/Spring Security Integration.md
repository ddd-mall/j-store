# Spring Security Integration

## Two Complementary Layers

Spring Security/Spring Authorization Server handles the OAuth 2.0/OIDC protocol, browser authentication and consent. The shared authentication SDK handles MVC business-controller annotations and principal injection through its access-token verification port. The runtime is not merely a standalone JWT filter or an interceptor-only substitute for Spring Security.

Business controllers use `@RequireLogin` and `@CurrentPrincipal AuthenticatedPrincipal`; explicitly anonymous User registration/verification endpoints use `@SkipLogin`. Old `CurrentUserIdArgumentResolver` and custom `JwtAuthenticationFilter` descriptions are obsolete.

**Sources** · [Spring Security configuration:59-66](file:///Users/101bjb0467/Sources/j-store/backend/j-store-user-boot/src/main/kotlin/com/jstore/user/oidc/OidcConfiguration.kt#L59-L66) · [Principal use in Checkout:80-105](file:///Users/101bjb0467/Sources/j-store/backend/j-store-trade-boot/src/main/kotlin/com/jstore/trade/controller/CheckoutController.kt#L80-L105) · [Anonymous User routes:79-96](file:///Users/101bjb0467/Sources/j-store/backend/j-store-user-boot/src/main/kotlin/com/jstore/user/controller/UserAccountController.kt#L79-L96)

## Verification and Identity

The configured `OidcAccessTokenVerifier` receives the JWT decoder, persistent authorization service, expected issuer/audience and session guard. Signature validation alone is insufficient: protocol authorization and session/account state must remain valid. Order and Checkout use both principal authentication domain and account ID for buyer isolation.

OAuth scopes/client registration express protocol permissions, not merchant membership or sales-policy authority. The SDK depends on authentication ports rather than User domain models.

**Sources** · [Verifier wiring:188-201](file:///Users/101bjb0467/Sources/j-store/backend/j-store-user-boot/src/main/kotlin/com/jstore/user/oidc/OidcConfiguration.kt#L188-L201) · [Buyer isolation:175-177](file:///Users/101bjb0467/Sources/j-store/docs/domain-modeling.md#L175-L177) · [Identity boundary:189-200](file:///Users/101bjb0467/Sources/j-store/docs/domain-modeling.md#L189-L200)

## Debugging Boundary

Check issuer/audience, client proof-key/consent configuration, authorization persistence, active account and Redis session/epoch state together. Do not restore old login endpoints, accept cross-domain bare account IDs, expose credentials or degrade checks when dependencies fail.
