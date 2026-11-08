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
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.properties.PhoneNumber
import com.jstore.user.domain.useraccount.*
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.nio.file.*
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.mock.web.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.*
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.util.UriComponentsBuilder

class OidcProtocolIntegrationTest {
    @Configuration @EnableWebMvc class Web

    private fun fixture(
        block: (MockMvc, AnnotationConfigWebApplicationContext, AccountSessionStore) -> Unit
    ) {
        EmbeddedPostgres.builder().start().use { postgres ->
            val port = java.net.ServerSocket(0).use { it.localPort }
            val redisServer =
                redis.embedded.RedisServer.newRedisServer()
                    .port(port)
                    .setting("bind 127.0.0.1")
                    .setting("save \"\"")
                    .setting("appendonly no")
                    .build()
            redisServer.start()
            val redisConnection =
                org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(
                    "127.0.0.1",
                    port,
                )
            redisConnection.afterPropertiesSet()
            redisConnection.start()
            val redisTemplate = StringRedisTemplate(redisConnection)
            redisTemplate.afterPropertiesSet()
            val dataSource = postgres.postgresDatabase
            val jdbc = JdbcTemplate(dataSource)
            jdbc.execute(
                "CREATE TABLE user_accounts(id bigint PRIMARY KEY, phone_number varchar(32))"
            )
            val file =
                Path.of(
                    "../j-store-boot/src/main/resources/db/migration/V20260507__baseline_j_store_boot_schema.sql"
                )
            jdbc.execute(
                Files.readString(file)
                    .substringAfter("-- User OIDC:")
                    .substringAfter(
                        "-- Adapted from Spring Authorization Server (Apache-2.0); see THIRD_PARTY.md."
                    )
            )
            jdbc.update("INSERT INTO user_accounts(id, phone_number) VALUES (42, '+8613800138000')")
            val keyFile = Files.createTempFile("oidc-test-", ".json")
            Files.writeString(
                keyFile,
                JWKSet(RSAKeyGenerator(2048).keyID("test-key").generate()).toString(false),
            )
            val context = AnnotationConfigWebApplicationContext()
            context.servletContext = MockServletContext()
            context.environment.setActiveProfiles("test")
            TestPropertyValues.of(
                    "jstore.oidc.issuer=https://identity.example",
                    "jstore.oidc.jwk-set-location=${keyFile.toUri()}",
                    "jstore.oidc.active-key-id=test-key",
                    "jstore.oidc.wechat.enabled=true",
                    "jstore.oidc.wechat.app-id=wechat-app",
                    "jstore.oidc.wechat.secret=test-wechat-secret",
                    "jstore.oidc.wechat.callback-uri=https://identity.example/login/wechat/callback",
                    "jstore.oidc.clients[0].id=web",
                    "jstore.oidc.clients[0].redirects[0]=https://client.example/callback",
                    "jstore.oidc.clients[0].secret-hash={bcrypt}" +
                        org.springframework.security.crypto.bcrypt
                            .BCryptPasswordEncoder()
                            .encode("test-client-secret"),
                    "jstore.oidc.clients[0].post-logout-redirects[0]=https://client.example/signed-out",
                    "jstore.oidc.clients[0].scopes[0]=openid",
                    "jstore.oidc.clients[0].scopes[1]=profile",
                    "jstore.oidc.clients[0].scopes[2]=offline_access",
                    "jstore.oidc.clients[0].scopes[3]=phone",
                    "jstore.oidc.clients[1].id=spa",
                    "jstore.oidc.clients[1].redirects[0]=https://client.example/callback",
                    "jstore.oidc.clients[2].id=other",
                    "jstore.oidc.clients[2].redirects[0]=https://other.example/callback",
                    "jstore.oidc.clients[2].secret-hash={bcrypt}" +
                        org.springframework.security.crypto.bcrypt
                            .BCryptPasswordEncoder()
                            .encode("other-secret"),
                )
                .applyTo(context.environment)
            val tokens = mock<AccountSessionStore>()
            whenever(tokens.isSessionActive(any(), any(), any())).thenReturn(true)
            whenever(tokens.createSession(any(), any(), any(), any())).thenReturn(true)
            val accounts = mock<UserAccountRepository>()
            val account =
                UserAccountImpl(
                    UserId(42),
                    PhoneNumber("+8613800138000"),
                    Nickname("alice"),
                    Password("hash"),
                    UserAccountStatus.ACTIVE,
                )
            whenever(accounts.findById(UserId(42))).thenReturn(account)
            whenever(accounts.findByPhoneNumber(account.phoneNumber)).thenReturn(account)
            val hasher = mock<PasswordHasher>()
            whenever(hasher.matches("Password1", "hash")).thenReturn(true)
            val attempts = mock<LoginAttemptGuard>()
            whenever(attempts.isAllowed(any())).thenReturn(true)
            val gateway = mock<com.jstore.user.domain.identity.WechatIdentityGateway>()
            whenever(gateway.exchange("verified-code"))
                .thenReturn(
                    com.jstore.user.domain.identity.VerifiedWechatIdentity(
                        com.jstore.user.domain.identity.ExternalIdentity(
                            "wechat",
                            "wechat-app",
                            "wx-subject",
                        ),
                        null,
                    )
                )
            val phones = mock<PhoneVerificationGateway>()
            whenever(phones.createChallenge(any(), eq("wechat-binding"), any()))
                .thenReturn(
                    IssuedPhoneVerificationChallenge(
                        PhoneVerificationChallenge(
                            "challenge",
                            java.time.Instant.now().plusSeconds(300),
                        ),
                        "123456",
                    )
                )
            whenever(phones.consumeChallenge(any(), any(), eq("wechat-binding"), any()))
                .thenReturn(true)
            context.addBeanFactoryPostProcessor { factory ->
                mapOf(
                        "dataSource" to dataSource,
                        "jdbc" to jdbc,
                        "transactions" to DataSourceTransactionManager(dataSource),
                        "tokens" to tokens,
                        "accounts" to accounts,
                        "hasher" to hasher,
                        "attempts" to attempts,
                        "factory" to mock<UserAccountFactory>(),
                        "phones" to phones,
                        "sender" to mock<PhoneVerificationCodeSender>(),
                        "events" to mock<DomainEventPublisher>(),
                        "redis" to redisTemplate,
                        "wechatGateway" to gateway,
                        "mapper" to ObjectMapper(),
                    )
                    .forEach(factory::registerSingleton)
            }
            context.register(
                Web::class.java,
                OidcConfiguration::class.java,
                LoginPageController::class.java,
                ConsentPageController::class.java,
                WechatLoginController::class.java,
            )
            try {
                context.refresh()
                block(
                    MockMvcBuilders.webAppContextSetup(context)
                        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(
                            springSecurity()
                        )
                        .build(),
                    context,
                    tokens,
                )
            } finally {
                context.close()
                Files.deleteIfExists(keyFile)
                redisConnection.destroy()
                redisServer.stop()
            }
        }
    }

    private val authentication
        get() =
            UsernamePasswordAuthenticationToken.authenticated(
                    "42",
                    null,
                    AuthorityUtils.NO_AUTHORITIES,
                )
                .apply {
                    details =
                        hashMapOf<String, Any>(
                            "sid" to "session",
                            "sev" to 0L,
                            "auth_time" to java.time.Instant.now().epochSecond,
                        )
                }

    @Test
    fun `OIDC code flow enforces PKCE and consent and refresh replay revokes access`() =
        fixture { mvc, context, _ ->
            val discovery =
                mvc.perform(get("/.well-known/openid-configuration")).andReturn().response
            assertEquals(200, discovery.status, discovery.contentAsString)
            assertTrue(discovery.contentAsString.contains("https://identity.example"))
            assertFalse(discovery.contentAsString.contains("client_credentials"))
            val session = MockHttpSession()
            val verifier = "a".repeat(43)
            val challenge =
                Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
                    )
            val authorization =
                mvc.perform(
                        get("/oauth2/authorize")
                            .session(session)
                            .with(authentication(authentication))
                            .queryParam("response_type", "code")
                            .queryParam("client_id", "web")
                            .queryParam("redirect_uri", "https://client.example/callback")
                            .queryParam("scope", "openid profile offline_access")
                            .queryParam("nonce", "nonce-value")
                            .queryParam("state", "client-state")
                            .queryParam("code_challenge_method", "S256")
                            .queryParam("code_challenge", challenge)
                    )
                    .andReturn()
                    .response
            assertEquals(302, authorization.status, authorization.contentAsString)
            val consentUri = assertNotNull(authorization.redirectedUrl)
            assertTrue(consentUri.contains("/consent"), consentUri)
            val state =
                UriComponentsBuilder.fromUriString(consentUri)
                    .build()
                    .queryParams
                    .getFirst("state")!!
                    .let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
            val page =
                mvc.perform(
                        get("/consent")
                            .session(session)
                            .with(authentication(authentication))
                            .param("client_id", "web")
                            .param("state", state)
                    )
                    .andReturn()
                    .response
            assertEquals(200, page.status, page.contentAsString)
            val csrfValue =
                Regex("name=\"_csrf\" value=\"([^\"]+)\"")
                    .find(page.contentAsString)!!
                    .groupValues[1]
            val missingCsrf =
                mvc.perform(
                        post("/oauth2/authorize")
                            .session(session)
                            .with(authentication(authentication))
                            .param("client_id", "web")
                            .param("state", state)
                            .param("scope", "openid", "profile", "offline_access")
                    )
                    .andReturn()
                    .response
            assertEquals(403, missingCsrf.status)
            val accepted =
                mvc.perform(
                        post("/oauth2/authorize")
                            .session(session)
                            .with(authentication(authentication))
                            .param("_csrf", csrfValue)
                            .param("client_id", "web")
                            .param("state", state)
                            .param("scope", "openid", "profile", "offline_access")
                    )
                    .andReturn()
                    .response
            assertEquals(302, accepted.status, accepted.contentAsString)
            val code =
                UriComponentsBuilder.fromUriString(assertNotNull(accepted.redirectedUrl))
                    .build()
                    .queryParams
                    .getFirst("code")!!
                    .let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
            val response =
                mvc.perform(
                        post("/oauth2/token")
                            .with(httpBasic("web", "test-client-secret"))
                            .param("grant_type", "authorization_code")
                            .param("code", code)
                            .param("redirect_uri", "https://client.example/callback")
                            .param("code_verifier", verifier)
                    )
                    .andReturn()
                    .response
            assertEquals(200, response.status, response.contentAsString)
            val json = ObjectMapper().readTree(response.contentAsString)
            val access = json.path("access_token").asText()
            val refresh = json.path("refresh_token").asText()
            assertTrue(
                access.isNotBlank() &&
                    refresh.isNotBlank() &&
                    json.path("id_token").asText().isNotBlank()
            )
            val sdk =
                context.getBean(com.jstore.authentication.principal.AccessTokenVerifier::class.java)
            assertNotNull(sdk.verifyAccessToken(access))
            assertNull(sdk.verifyAccessToken(json.path("id_token").asText()))
            val userinfo =
                mvc.perform(get("/userinfo").header("Authorization", "Bearer $access"))
                    .andReturn()
                    .response
            assertEquals(200, userinfo.status, userinfo.contentAsString)
            assertFalse(userinfo.contentAsString.contains("phone_number"))
            val rotated =
                mvc.perform(
                        post("/oauth2/token")
                            .with(httpBasic("web", "test-client-secret"))
                            .param("grant_type", "refresh_token")
                            .param("refresh_token", refresh)
                    )
                    .andReturn()
                    .response
            assertEquals(200, rotated.status, rotated.contentAsString)
            val replacementAccess =
                ObjectMapper().readTree(rotated.contentAsString).path("access_token").asText()
            val replay =
                mvc.perform(
                        post("/oauth2/token")
                            .with(httpBasic("web", "test-client-secret"))
                            .param("grant_type", "refresh_token")
                            .param("refresh_token", refresh)
                    )
                    .andReturn()
                    .response
            assertEquals(400, replay.status, replay.contentAsString)
            assertNull(sdk.verifyAccessToken(replacementAccess))
        }

    private fun issue(
        mvc: MockMvc,
        client: String = "web",
        scope: String = "openid profile offline_access",
    ): com.fasterxml.jackson.databind.JsonNode {
        val session = MockHttpSession()
        val verifier = "a".repeat(43)
        val challenge =
            Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val response =
            mvc.perform(
                    get("/oauth2/authorize")
                        .session(session)
                        .with(authentication(authentication))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", client)
                        .queryParam("redirect_uri", "https://client.example/callback")
                        .queryParam("scope", scope)
                        .queryParam("nonce", "nonce-value")
                        .queryParam("state", "state")
                        .queryParam("code_challenge_method", "S256")
                        .queryParam("code_challenge", challenge)
                )
                .andReturn()
                .response
        assertEquals(302, response.status, response.contentAsString)
        val location = assertNotNull(response.redirectedUrl)
        val consentState =
            UriComponentsBuilder.fromUriString(location)
                .build()
                .queryParams
                .getFirst("state")!!
                .let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
        val page =
            mvc.perform(
                    get("/consent")
                        .session(session)
                        .with(authentication(authentication))
                        .param("client_id", client)
                        .param("state", consentState)
                )
                .andReturn()
                .response
        val csrfValue =
            Regex("name=\"_csrf\" value=\"([^\"]+)\"").find(page.contentAsString)!!.groupValues[1]
        val accepted =
            mvc.perform(
                    post("/oauth2/authorize")
                        .session(session)
                        .with(authentication(authentication))
                        .param("_csrf", csrfValue)
                        .param("client_id", client)
                        .param("state", consentState)
                        .param("scope", *scope.split(' ').toTypedArray())
                )
                .andReturn()
                .response
        val code =
            UriComponentsBuilder.fromUriString(assertNotNull(accepted.redirectedUrl))
                .build()
                .queryParams
                .getFirst("code")!!
                .let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
        val exchange =
            post("/oauth2/token")
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", "https://client.example/callback")
                .param("code_verifier", verifier)
        if (client == "web") exchange.with(httpBasic("web", "test-client-secret"))
        else exchange.param("client_id", client)
        val token = mvc.perform(exchange).andReturn().response
        assertEquals(200, token.status, token.contentAsString)
        return ObjectMapper().readTree(token.contentAsString)
    }

    @Test
    fun `public clients get no refresh credentials and ID token is never API credential`() =
        fixture { mvc, context, _ ->
            val token = issue(mvc, "spa", "openid profile")
            assertFalse(token.has("refresh_token"))
            val id = com.nimbusds.jwt.SignedJWT.parse(token.path("id_token").asText()).jwtClaimsSet
            assertEquals(listOf("spa"), id.audience)
            assertEquals("nonce-value", id.getStringClaim("nonce"))
            assertEquals("42", id.subject)
            assertFalse(id.claims.containsKey("phone_number"))
            assertNull(
                context
                    .getBean(com.jstore.authentication.principal.AccessTokenVerifier::class.java)
                    .verifyAccessToken(token.path("id_token").asText())
            )
            val keys =
                ObjectMapper()
                    .readTree(mvc.perform(get("/oauth2/jwks")).andReturn().response.contentAsString)
                    .path("keys")
            assertTrue(keys.size() > 0)
            assertTrue(keys.none { it.has("d") || it.has("p") || it.has("q") })
        }

    @Test
    fun `phone disclosure follows access scope and cross client introspection is inactive`() =
        fixture { mvc, _, _ ->
            val token = issue(mvc, scope = "openid profile phone offline_access")
            val access = token.path("access_token").asText()
            val userinfo =
                mvc.perform(get("/userinfo").header("Authorization", "Bearer $access"))
                    .andReturn()
                    .response
            assertEquals(200, userinfo.status, userinfo.contentAsString)
            assertTrue(userinfo.contentAsString.contains("phone_number"))
            val other =
                mvc.perform(
                        post("/oauth2/introspect")
                            .with(httpBasic("other", "other-secret"))
                            .param("token", access)
                    )
                    .andReturn()
                    .response
            assertEquals(200, other.status, other.contentAsString)
            assertFalse(ObjectMapper().readTree(other.contentAsString).path("active").asBoolean())
            val rotated =
                mvc.perform(
                        post("/oauth2/token")
                            .with(httpBasic("web", "test-client-secret"))
                            .param("grant_type", "refresh_token")
                            .param("refresh_token", token.path("refresh_token").asText())
                            .param("scope", "openid profile offline_access")
                    )
                    .andReturn()
                    .response
            assertEquals(200, rotated.status, rotated.contentAsString)
            val narrowedAccess =
                ObjectMapper().readTree(rotated.contentAsString).path("access_token").asText()
            val narrowed =
                mvc.perform(get("/userinfo").header("Authorization", "Bearer $narrowedAccess"))
                    .andReturn()
                    .response
            assertEquals(200, narrowed.status, narrowed.contentAsString)
            assertFalse(narrowed.contentAsString.contains("phone_number"))
        }

    @Test
    fun `server side session revocation rejects access and refresh immediately`() =
        fixture { mvc, context, tokens ->
            val token = issue(mvc)
            val verifier =
                context.getBean(com.jstore.authentication.principal.AccessTokenVerifier::class.java)
            assertNotNull(verifier.verifyAccessToken(token.path("access_token").asText()))
            whenever(tokens.isSessionActive(any(), any(), any())).thenReturn(false)
            assertNull(verifier.verifyAccessToken(token.path("access_token").asText()))
            val refresh =
                mvc.perform(
                        post("/oauth2/token")
                            .with(httpBasic("web", "test-client-secret"))
                            .param("grant_type", "refresh_token")
                            .param("refresh_token", token.path("refresh_token").asText())
                    )
                    .andReturn()
                    .response
            assertEquals(400, refresh.status, refresh.contentAsString)
        }

    @Test
    fun `local login rotates browser session and requires CSRF`() = fixture { mvc, _, _ ->
        val session = MockHttpSession()
        val oldId = session.id
        val page = mvc.perform(get("/login").session(session)).andReturn().response
        val csrfValue =
            Regex("name=\"_csrf\" value=\"([^\"]+)\"").find(page.contentAsString)!!.groupValues[1]
        assertEquals(
            403,
            mvc.perform(
                    post("/login")
                        .session(session)
                        .param("username", "+8613800138000")
                        .param("password", "Password1")
                )
                .andReturn()
                .response
                .status,
        )
        val response =
            mvc.perform(
                    post("/login")
                        .session(session)
                        .param("_csrf", csrfValue)
                        .param("username", "+8613800138000")
                        .param("password", "Password1")
                )
                .andReturn()
                .response
        assertEquals(302, response.status)
        assertNotEquals(oldId, session.id)
        assertFalse(response.contentAsString.contains("access_token"))
    }

    @Test
    fun `wechat onboarding verifies phone before binding and creating browser login`() =
        fixture { mvc, context, _ ->
            val session = MockHttpSession()
            val oldId = session.id
            val started = mvc.perform(get("/login/wechat").session(session)).andReturn().response
            assertEquals(302, started.status)
            val state =
                UriComponentsBuilder.fromUriString(assertNotNull(started.redirectedUrl))
                    .build()
                    .queryParams
                    .getFirst("state")!!
            val wrongBrowser =
                mvc.perform(
                        get("/login/wechat/callback")
                            .session(MockHttpSession())
                            .param("state", state)
                            .param("code", "verified-code")
                    )
                    .andReturn()
                    .response
            assertEquals("/login?error", wrongBrowser.redirectedUrl)
            val callback =
                mvc.perform(
                        get("/login/wechat/callback")
                            .session(session)
                            .param("state", state)
                            .param("code", "verified-code")
                    )
                    .andReturn()
                    .response
            assertEquals("/login/wechat/onboarding", callback.redirectedUrl)
            assertNull(session.getAttribute("SPRING_SECURITY_CONTEXT"))
            val form =
                mvc.perform(get("/login/wechat/onboarding").session(session)).andReturn().response
            val csrfValue =
                Regex("name=\"_csrf\" value=\"([^\"]+)\"")
                    .find(form.contentAsString)!!
                    .groupValues[1]
            val sent =
                mvc.perform(
                        post("/login/wechat/onboarding")
                            .session(session)
                            .param("_csrf", csrfValue)
                            .param("action", "send")
                            .param("phone", "+8613800138000")
                    )
                    .andReturn()
                    .response
            assertEquals(200, sent.status, sent.contentAsString)
            assertNull(session.getAttribute("SPRING_SECURITY_CONTEXT"))
            val completed =
                mvc.perform(
                        post("/login/wechat/onboarding")
                            .session(session)
                            .param("_csrf", csrfValue)
                            .param("action", "bind")
                            .param("confirm", "true")
                            .param("phone", "+8613800138000")
                            .param("challengeId", "challenge")
                            .param("code", "123456")
                    )
                    .andReturn()
                    .response
            assertEquals(302, completed.status, completed.contentAsString)
            assertNotEquals(oldId, session.id)
            assertNotNull(session.getAttribute("SPRING_SECURITY_CONTEXT"))
            val binding =
                context
                    .getBean(
                        com.jstore.user.domain.identity.ExternalIdentityBindingRepository::class
                            .java
                    )
                    .findByIdentity(
                        com.jstore.user.domain.identity.ExternalIdentity(
                            "wechat",
                            "wechat-app",
                            "wx-subject",
                        )
                    )
            assertEquals(UserId(42), binding?.userId)
            val replay =
                mvc.perform(
                        get("/login/wechat/callback")
                            .session(session)
                            .param("state", state)
                            .param("code", "verified-code")
                    )
                    .andReturn()
                    .response
            assertEquals("/login?error", replay.redirectedUrl)
        }

    @Test
    fun `RP logout restricts return URI and revokes the current account session`() =
        fixture { mvc, context, tokens ->
            val token = issue(mvc)
            val idToken = token.path("id_token").asText()
            val wrong =
                mvc.perform(
                        get("/connect/logout")
                            .with(authentication(authentication))
                            .queryParam("id_token_hint", idToken)
                            .queryParam("post_logout_redirect_uri", "https://evil.example/")
                    )
                    .andReturn()
                    .response
            assertEquals(400, wrong.status, wrong.contentAsString)
            doAnswer {
                    whenever(tokens.isSessionActive(any(), any(), any())).thenReturn(false)
                    null
                }
                .whenever(tokens)
                .revokeSession(UserId(42), "session")
            val logout =
                mvc.perform(
                        get("/connect/logout")
                            .with(authentication(authentication))
                            .queryParam("id_token_hint", idToken)
                            .queryParam(
                                "post_logout_redirect_uri",
                                "https://client.example/signed-out",
                            )
                    )
                    .andReturn()
                    .response
            assertEquals(302, logout.status, logout.contentAsString)
            assertEquals("https://client.example/signed-out", logout.redirectedUrl)
            verify(tokens).revokeSession(UserId(42), "session")
            assertNull(
                context
                    .getBean(com.jstore.authentication.principal.AccessTokenVerifier::class.java)
                    .verifyAccessToken(token.path("access_token").asText())
            )
        }

    @Test
    fun `authorization rejects unregistered redirects missing PKCE and disallowed scopes`() =
        fixture { mvc, _, _ ->
            fun request(redirect: String, scope: String) =
                get("/oauth2/authorize")
                    .with(authentication(authentication))
                    .queryParam("response_type", "code")
                    .queryParam("client_id", "spa")
                    .queryParam("redirect_uri", redirect)
                    .queryParam("scope", scope)
            val redirect =
                mvc.perform(request("https://evil.example/callback", "openid")).andReturn().response
            assertEquals(400, redirect.status)
            assertNull(redirect.redirectedUrl)
            val missing =
                mvc.perform(request("https://client.example/callback", "openid"))
                    .andReturn()
                    .response
            assertTrue(missing.redirectedUrl?.contains("error=") == true, missing.contentAsString)
            assertFalse(missing.redirectedUrl.orEmpty().contains("?code="))
            val badScope =
                mvc.perform(
                        request("https://client.example/callback", "openid phone")
                            .queryParam("code_challenge_method", "S256")
                            .queryParam("code_challenge", "a".repeat(43))
                    )
                    .andReturn()
                    .response
            assertTrue(
                badScope.redirectedUrl?.contains("error=invalid_scope") == true,
                badScope.contentAsString,
            )
            val unsupported =
                mvc.perform(
                        post("/oauth2/token")
                            .with(httpBasic("web", "test-client-secret"))
                            .param("grant_type", "client_credentials")
                    )
                    .andReturn()
                    .response
            assertEquals(400, unsupported.status, unsupported.contentAsString)
        }

    @Test
    fun `UserInfo without a bearer token returns 401 instead of a browser redirect`() =
        fixture { mvc, _, _ ->
            val response = mvc.perform(get("/userinfo")).andReturn().response
            assertEquals(401, response.status)
            assertNull(response.redirectedUrl)
        }
}
