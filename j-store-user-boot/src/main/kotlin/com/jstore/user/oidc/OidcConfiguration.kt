/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.user.oidc

import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.authentication.principal.AccessTokenVerifier
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.user.domain.identity.*
import com.jstore.user.domain.useraccount.*
import com.jstore.user.service.*
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.jwk.source.*
import com.nimbusds.jose.proc.*
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import java.security.Principal
import java.time.*
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
import org.springframework.context.annotation.*
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.core.io.ResourceLoader
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.*
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.*
import org.springframework.security.oauth2.core.oidc.*
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.oauth2.server.authorization.*
import org.springframework.security.oauth2.server.authorization.authentication.*
import org.springframework.security.oauth2.server.authorization.client.*
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer
import org.springframework.security.oauth2.server.authorization.settings.*
import org.springframework.security.oauth2.server.authorization.token.*
import org.springframework.security.web.*
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.cors.*

@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(
    type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET
)
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(OidcProperties::class)
@DependsOnDatabaseInitialization
class OidcConfiguration {
    @Bean fun oidcClock(): Clock = Clock.systemUTC()

    @Bean
    fun signingKeys(
        properties: OidcProperties,
        resources: ResourceLoader,
        environment: Environment,
    ): JWKSet {
        properties.validate(environment.activeProfiles.any { it in setOf("local", "dev", "test") })
        val set =
            resources.getResource(properties.jwkSetLocation).inputStream.use {
                JWKSet.parse(it.bufferedReader().readText())
            }
        val active = set.getKeyByKeyId(properties.activeKeyId) as? RSAKey
        require(active != null && active.isPrivate && active.size() >= 2048) {
            "Configure an RSA signing key of at least 2048 bits with the active kid"
        }
        require(set.keys.all { it is RSAKey && it.keyID != null && it.size() >= 2048 })
        require(set.keys.map { it.keyID }.toSet().size == set.keys.size)
        return set
    }

    @Bean fun jwkSource(keys: JWKSet): JWKSource<SecurityContext> = ImmutableJWKSet(keys)

    @Bean
    fun oidcJwtDecoder(keys: JWKSet, properties: OidcProperties, clock: Clock): JwtDecoder {
        val processor = DefaultJWTProcessor<SecurityContext>()
        processor.jwsKeySelector =
            JWSVerificationKeySelector(JWSAlgorithm.RS256, ImmutableJWKSet(keys.toPublicJWKSet()))
        return NimbusJwtDecoder(processor).apply {
            setJwtValidator(
                org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator(
                    JwtTimestampValidator(Duration.ZERO).apply { setClock(clock) },
                    JwtIssuerValidator(properties.issuer),
                )
            )
        }
    }

    @Bean
    fun authorizationSettings(properties: OidcProperties): AuthorizationServerSettings =
        AuthorizationServerSettings.builder().issuer(properties.issuer).build()

    @Bean
    fun registeredClients(
        jdbc: JdbcTemplate,
        properties: OidcProperties,
        signingKeys: JWKSet,
        manager: PlatformTransactionManager,
    ): RegisteredClientRepository {
        val delegate = JdbcRegisteredClientRepository(jdbc)
        // Resolve validated signing material before mutating shared client configuration.
        require(signingKeys.keys.isNotEmpty())
        org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult {
            properties.clients.forEach { config ->
                val builder =
                    RegisteredClient.withId(config.id)
                        .clientId(config.id)
                        .clientName(config.name.ifBlank { config.id })
                        .clientAuthenticationMethod(
                            if (config.confidential) ClientAuthenticationMethod.CLIENT_SECRET_BASIC
                            else ClientAuthenticationMethod.NONE
                        )
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .clientSettings(
                            ClientSettings.builder()
                                .requireProofKey(true)
                                .requireAuthorizationConsent(true)
                                .setting("jstore.audiences", config.audiences.toList())
                                .build()
                        )
                        .tokenSettings(
                            TokenSettings.builder()
                                .authorizationCodeTimeToLive(Duration.ofSeconds(60))
                                .accessTokenTimeToLive(Duration.ofMinutes(15))
                                .refreshTokenTimeToLive(Duration.ofDays(7))
                                .reuseRefreshTokens(false)
                                .build()
                        )
                if (config.confidential) builder.clientSecret(config.secretHash)
                if (config.confidential && "offline_access" in config.scopes)
                    builder.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                config.redirects.forEach(builder::redirectUri)
                config.postLogoutRedirects.forEach(builder::postLogoutRedirectUri)
                config.scopes.forEach(builder::scope)
                delegate.save(builder.build())
            }
        }
        val allowed = properties.clients.map { it.id }.toSet()
        return object : RegisteredClientRepository {
            override fun save(client: RegisteredClient) {
                require(client.clientId in allowed)
                delegate.save(client)
            }

            override fun findById(id: String): RegisteredClient? =
                if (id in allowed) delegate.findById(id) else null

            override fun findByClientId(id: String): RegisteredClient? =
                if (id in allowed) delegate.findByClientId(id) else null
        }
    }

    @Bean
    fun consentService(
        jdbc: JdbcTemplate,
        clients: RegisteredClientRepository,
    ): OAuth2AuthorizationConsentService = JdbcOAuth2AuthorizationConsentService(jdbc, clients)

    @Bean
    fun authorizationService(
        jdbc: JdbcTemplate,
        clients: RegisteredClientRepository,
        guard: OidcSessionGuard,
    ): DigestAuthorizationService =
        DigestAuthorizationService(jdbc, clients, guard::authorizationActive)

    @Bean
    fun sessionGuard(accounts: UserAccountRepository, tokens: AccountSessionStore, clock: Clock) =
        OidcSessionGuard(accounts, tokens, clock)

    @Bean
    fun accessTokenVerifier(
        decoder: JwtDecoder,
        authorizations: DigestAuthorizationService,
        guard: OidcSessionGuard,
        properties: OidcProperties,
    ): AccessTokenVerifier =
        OidcAccessTokenVerifier(
            decoder,
            authorizations,
            properties.issuer,
            properties.resourceAudience,
            guard::authorizationActive,
        )

    @Bean
    fun credentialService(
        accounts: UserAccountRepository,
        hasher: PasswordHasher,
        attempts: LoginAttemptGuard,
    ) = AccountCredentialsService(accounts, hasher, attempts)

    @Bean
    fun localAuthenticationProvider(
        credentials: AccountCredentialsService,
        tokens: AccountSessionStore,
        clock: Clock,
        jdbc: JdbcTemplate,
        manager: PlatformTransactionManager,
    ) = LocalAccountAuthenticationProvider(credentials, tokens, clock, jdbc, manager)

    @Bean
    fun externalBindings(jdbc: JdbcTemplate): ExternalIdentityBindingRepository =
        JdbcExternalIdentityBindingRepository(jdbc)

    @Bean
    fun bindingService(
        accounts: UserAccountRepository,
        bindings: ExternalIdentityBindingRepository,
        factory: UserAccountFactory,
        hasher: PasswordHasher,
        phones: PhoneVerificationGateway,
        events: DomainEventPublisher,
    ) = ExternalAccountBindingService(accounts, bindings, factory, hasher, phones, events)

    @Bean fun loginIntents(redis: StringRedisTemplate) = RedisLoginIntentStore(redis)

    @Bean
    fun wechatGateway(properties: OidcProperties, mapper: ObjectMapper): WechatIdentityGateway =
        HttpWechatIdentityGateway(properties.wechat.appId, properties.wechat.secret, mapper)

    @Bean
    fun tokenGenerator(
        source: JWKSource<SecurityContext>,
        properties: OidcProperties,
        guard: OidcSessionGuard,
        clock: Clock,
    ): OAuth2TokenGenerator<OAuth2Token> {
        val jwt = JwtGenerator(NimbusJwtEncoder(source))
        jwt.setJwtCustomizer { context ->
            val principal = context.getPrincipal<Authentication>()
            if (
                !context.registeredClient.scopes.containsAll(context.authorizedScopes) ||
                    !guard.authenticationActive(principal)
            )
                throw OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT)
            val grant =
                context.getAuthorizationGrant<Authentication>()
                    as? OAuth2AuthorizationGrantAuthenticationToken
            if (
                grant != null &&
                    (grant.additionalParameters.containsKey("audience") ||
                        grant.additionalParameters.containsKey("resource"))
            )
                throw OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST)
            val details = principal.details as Map<*, *>
            context.jwsHeader.keyId(properties.activeKeyId)
            val now = clock.instant()
            context.claims
                .issuedAt(now)
                .expiresAt(
                    now.plusSeconds(
                        if (context.tokenType == OAuth2TokenType.ACCESS_TOKEN) 900 else 300
                    )
                )
            context.claims.claim("auth_time", details["auth_time"])
            if (context.tokenType == OAuth2TokenType.ACCESS_TOKEN) {
                context.claims
                    .claim("sid", details["sid"])
                    .claim("sev", details["sev"])
                    .claim("token_use", "access")
                    .claim("aid", requireNotNull(context.authorization).id)
                    .audience(
                        requireNotNull(
                            context.registeredClient.clientSettings.getSetting<List<String>>(
                                "jstore.audiences"
                            )
                        )
                    )
            }
        }
        return OAuth2TokenGenerator { context ->
            if (context.tokenType == OAuth2TokenType.REFRESH_TOKEN) {
                if ("offline_access" !in context.authorizedScopes) null
                else {
                    val now = clock.instant()
                    val expires =
                        context.authorization?.refreshToken?.token?.expiresAt
                            ?: now.plus(Duration.ofDays(7))
                    if (expires <= now)
                        throw OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT)
                    OAuth2RefreshToken(RedisLoginIntentStore.randomId(), now, expires)
                }
            } else jwt.generate(context)
        }
    }

    @Bean
    @Order(1)
    fun authorizationChain(
        http: HttpSecurity,
        clients: RegisteredClientRepository,
        authorizations: DigestAuthorizationService,
        clock: Clock,
        manager: PlatformTransactionManager,
        guard: OidcSessionGuard,
    ): SecurityFilterChain {
        http.headers {
            it.referrerPolicy { policy ->
                policy.policy(
                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                        .ReferrerPolicy
                        .NO_REFERRER
                )
            }
        }
        val server = OAuth2AuthorizationServerConfigurer.authorizationServer()
        http
            .securityMatcher(server.endpointsMatcher)
            .with(server) { config ->
                config.authorizationEndpoint { endpoint ->
                    endpoint.consentPage("/consent")
                    endpoint.authenticationProviders { providers ->
                        val codeGenerator =
                            OAuth2TokenGenerator<OAuth2AuthorizationCode> { context ->
                                val now = clock.instant()
                                OAuth2AuthorizationCode(
                                    RedisLoginIntentStore.randomId(),
                                    now,
                                    now.plus(
                                        context.registeredClient.tokenSettings
                                            .authorizationCodeTimeToLive
                                    ),
                                )
                            }
                        providers
                            .filterIsInstance<OAuth2AuthorizationConsentAuthenticationProvider>()
                            .forEach { it.setAuthorizationCodeGenerator(codeGenerator) }
                        providers
                            .filterIsInstance<
                                OAuth2AuthorizationCodeRequestAuthenticationProvider
                            >()
                            .forEach { provider ->
                                provider.setAuthorizationCodeGenerator(codeGenerator)
                                provider.setAuthorizationConsentRequired { context ->
                                    val request =
                                        context.getAuthentication<
                                            OAuth2AuthorizationCodeRequestAuthenticationToken
                                        >()
                                    "consent" in
                                        (request.additionalParameters["prompt"] as? String)
                                            .orEmpty()
                                            .split(' ') ||
                                        context.authorizationConsent == null ||
                                        !context.authorizationConsent!!
                                            .scopes
                                            .containsAll(request.scopes.filter { it != "openid" })
                                }
                                provider.setAuthenticationValidator { context ->
                                    OAuth2AuthorizationCodeRequestAuthenticationValidator
                                        .DEFAULT_REDIRECT_URI_VALIDATOR
                                        .accept(context)
                                    OAuth2AuthorizationCodeRequestAuthenticationValidator
                                        .DEFAULT_SCOPE_VALIDATOR
                                        .accept(context)
                                    val request =
                                        context.getAuthentication<
                                            OAuth2AuthorizationCodeRequestAuthenticationToken
                                        >()
                                    if (
                                        (request.additionalParameters["max_age"]
                                            ?.toString()
                                            ?.toLongOrNull()
                                            ?.let { it < 0 }
                                            ?: request.additionalParameters.containsKey(
                                                "max_age"
                                            )) ||
                                            request.additionalParameters["code_challenge_method"] !=
                                                "S256" ||
                                            !(request.additionalParameters["code_challenge"]
                                                    as? String)
                                                .orEmpty()
                                                .matches(Regex("[A-Za-z0-9_-]{43}")) ||
                                            request.additionalParameters.containsKey("audience") ||
                                            request.additionalParameters.containsKey("resource")
                                    ) {
                                        throw OAuth2AuthorizationCodeRequestAuthenticationException(
                                            OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST),
                                            request,
                                        )
                                    }
                                }
                            }
                    }
                }
                config.oidc { oidc ->
                    oidc.logoutEndpoint { endpoint ->
                        endpoint.authenticationProviders { providers ->
                            providers
                                .filterIsInstance<
                                    org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationProvider
                                >()
                                .forEach { provider ->
                                    provider.setAuthenticationValidator { context ->
                                        org.springframework.security.oauth2.server.authorization
                                            .oidc
                                            .authentication
                                            .OidcLogoutAuthenticationValidator()
                                            .accept(context)
                                        val request =
                                            context.getAuthentication<
                                                org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken
                                            >()
                                        if (request.isPrincipalAuthenticated) {
                                            val current = request.principal as Authentication
                                            val saved =
                                                authorizations
                                                    .findByToken(
                                                        request.idTokenHint,
                                                        OAuth2TokenType("id_token"),
                                                    )
                                                    ?.getAttribute<Authentication>(
                                                        Principal::class.java.name
                                                    )
                                            val currentSid =
                                                (current.details as? Map<*, *>)?.get("sid")
                                            val savedSid =
                                                (saved?.details as? Map<*, *>)?.get("sid")
                                            if (currentSid == null || currentSid != savedSid)
                                                throw OAuth2AuthenticationException(
                                                    OAuth2ErrorCodes.INVALID_TOKEN
                                                )
                                        }
                                    }
                                }
                        }
                        val handler =
                            org.springframework.security.oauth2.server.authorization.oidc.web
                                .authentication
                                .OidcLogoutAuthenticationSuccessHandler()
                        handler.setLogoutHandler { request, response, authentication ->
                            val principal = authentication.principal as? Authentication
                            if (
                                principal != null &&
                                    principal.isAuthenticated &&
                                    principal !is AnonymousAuthenticationToken
                            )
                                guard.revoke(principal)
                            org.springframework.security.web.authentication.logout
                                .SecurityContextLogoutHandler()
                                .logout(request, response, principal)
                        }
                        endpoint.logoutResponseHandler(handler)
                    }
                    oidc.providerConfigurationEndpoint { endpoint ->
                        endpoint.providerConfigurationCustomizer {
                            it.claims { claims ->
                                claims.remove("device_authorization_endpoint")
                                claims.remove("dpop_signing_alg_values_supported")
                                claims["tls_client_certificate_bound_access_tokens"] = false
                                claims["revocation_endpoint_auth_methods_supported"] =
                                    listOf("client_secret_basic")
                                claims["introspection_endpoint_auth_methods_supported"] =
                                    listOf("client_secret_basic")
                                claims["scopes_supported"] =
                                    listOf("openid", "profile", "phone", "offline_access")
                                claims["token_endpoint_auth_methods_supported"] =
                                    listOf("none", "client_secret_basic")
                                claims["prompt_values_supported"] =
                                    listOf("none", "login", "consent")
                                claims["code_challenge_methods_supported"] = listOf("S256")
                                claims["grant_types_supported"] =
                                    listOf("authorization_code", "refresh_token")
                            }
                        }
                    }
                    oidc.userInfoEndpoint { endpoint ->
                        endpoint.userInfoMapper { context ->
                            val authorization = context.authorization
                            if (!guard.authorizationActive(authorization))
                                throw OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_TOKEN)
                            OidcUserInfo(
                                guard.userInfo(
                                    authorization.principalName,
                                    authorization.accessToken.token.scopes,
                                )
                            )
                        }
                    }
                }
                config.authorizationServerMetadataEndpoint { endpoint ->
                    endpoint.authorizationServerMetadataCustomizer {
                        it.claims { claims ->
                            claims.remove("device_authorization_endpoint")
                            claims.remove("dpop_signing_alg_values_supported")
                            claims["tls_client_certificate_bound_access_tokens"] = false
                            claims["revocation_endpoint_auth_methods_supported"] =
                                listOf("client_secret_basic")
                            claims["introspection_endpoint_auth_methods_supported"] =
                                listOf("client_secret_basic")
                            claims["scopes_supported"] =
                                listOf("openid", "profile", "phone", "offline_access")
                            claims["token_endpoint_auth_methods_supported"] =
                                listOf("none", "client_secret_basic")
                            claims["prompt_values_supported"] = listOf("none", "login", "consent")
                            claims["code_challenge_methods_supported"] = listOf("S256")
                            claims["grant_types_supported"] =
                                listOf("authorization_code", "refresh_token")
                        }
                    }
                }
            }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .exceptionHandling {
                it.authenticationEntryPoint(
                    org.springframework.security.oauth2.server.resource.web
                        .BearerTokenAuthenticationEntryPoint()
                )
                it.defaultAuthenticationEntryPointFor(
                    LoginUrlAuthenticationEntryPoint("/login"),
                    org.springframework.security.web.util.matcher.AntPathRequestMatcher(
                        "/oauth2/authorize"
                    ),
                )
            }
            .oauth2ResourceServer { oauth ->
                val resolver =
                    org.springframework.security.oauth2.server.resource.web
                        .DefaultBearerTokenResolver()
                oauth.bearerTokenResolver { request ->
                    if (request.requestURI == request.contextPath + "/userinfo")
                        resolver.resolve(request)
                    else null
                }
                oauth.jwt {}
            }
            .addFilterBefore(
                ProtocolTransactionFilter(manager),
                SecurityContextHolderFilter::class.java,
            )
            .addFilterAfter(
                OidcBrowserSessionFilter(guard),
                SecurityContextHolderFilter::class.java,
            )
            .addFilterAfter(ConsentCsrfFilter(), SecurityContextHolderFilter::class.java)
            .addFilterAfter(BearerOnlyProtocolFilter(), SecurityContextHolderFilter::class.java)
            .addFilterAfter(
                OidcReauthenticationFilter(clients, clock),
                SecurityContextHolderFilter::class.java,
            )
        return http.build()
    }

    @Bean
    @Order(2)
    fun loginChain(
        http: HttpSecurity,
        properties: OidcProperties,
        provider: LocalAccountAuthenticationProvider,
        guard: OidcSessionGuard,
    ): SecurityFilterChain {
        http
            .securityMatcher("/login", "/login/**", "/consent")
            .authorizeHttpRequests {
                it.requestMatchers("/consent").authenticated().anyRequest().permitAll()
            }
            .authenticationProvider(provider)
            .formLogin {
                it.loginPage("/login")
                    .loginProcessingUrl("/login")
                    .failureUrl("/login?error")
                    .successHandler { request, response, _ ->
                        response.sendRedirect(
                            authorizationContinuation(
                                request,
                                response,
                                properties,
                            )
                        )
                    }
                    .permitAll()
            }
            .addFilterAfter(
                OidcBrowserSessionFilter(guard),
                SecurityContextHolderFilter::class.java,
            )
        return http.build()
    }

    @Bean
    @Order(3)
    fun apiChain(http: HttpSecurity, properties: OidcProperties): SecurityFilterChain {
        // Authentication SDK remains authoritative for business HandlerMethods.
        http
            .securityMatcher("/**")
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .csrf { it.disable() }
            .requestCache { it.disable() }
            .securityContext { it.disable() }
            .sessionManagement {
                it.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS
                )
            }
            .cors { cors ->
                val source = UrlBasedCorsConfigurationSource()
                source.registerCorsConfiguration(
                    "/**",
                    CorsConfiguration().apply {
                        allowedOrigins = properties.clients.flatMap { it.corsOrigins }.distinct()
                        allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        allowedHeaders = listOf("Authorization", "Content-Type")
                        allowCredentials = false
                    },
                )
                cors.configurationSource(source)
            }
        return http.build()
    }
}
