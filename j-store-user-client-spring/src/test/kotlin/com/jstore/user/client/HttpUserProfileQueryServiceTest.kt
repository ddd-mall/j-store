package com.jstore.user.client

import com.jstore.user.api.UserProfileStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class HttpUserProfileQueryServiceTest {
    private val token = "a".repeat(32)
    private val builder = RestClient.builder().baseUrl("http://user-service")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val service =
        HttpUserProfileQueryService(
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $token").build()
        )

    @Test
    fun `successful response maps the complete profile and sends service credential`() {
        server
            .expect(once(), requestTo("http://user-service/internal/api/users/42/profile"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andRespond(
                withSuccess(
                    """{"userId":42,"nickname":"buyer","phoneNumber":"+8613800138000","status":"ACTIVE"}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val profile = service.findById(42)!!

        assertEquals("buyer", profile.nickname)
        assertEquals("+8613800138000", profile.phoneNumber)
        assertEquals(UserProfileStatus.ACTIVE, profile.status)
        server.verify()
    }

    @Test
    fun `not found is the only HTTP failure mapped to missing user`() {
        server
            .expect(requestTo("http://user-service/internal/api/users/404/profile"))
            .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND))

        assertNull(service.findById(404))
        server.verify()
    }

    @Test
    fun `unauthorized response is propagated as a dependency failure`() {
        server
            .expect(requestTo("http://user-service/internal/api/users/42/profile"))
            .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED))

        assertFailsWith<UserProfileDependencyException> { service.findById(42) }
        server.verify()
    }

    @Test
    fun `server error is propagated as a dependency failure`() {
        server
            .expect(requestTo("http://user-service/internal/api/users/42/profile"))
            .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE))

        assertFailsWith<UserProfileDependencyException> { service.findById(42) }
        server.verify()
    }

    @Test
    fun `empty response is propagated as a dependency failure`() {
        server
            .expect(requestTo("http://user-service/internal/api/users/42/profile"))
            .andRespond(withSuccess())

        assertFailsWith<UserProfileDependencyException> { service.findById(42) }
        server.verify()
    }

    @Test
    fun `invalid profile response is propagated as a dependency failure`() {
        server
            .expect(requestTo("http://user-service/internal/api/users/42/profile"))
            .andRespond(
                withSuccess(
                    """{"userId":42,"nickname":"","phoneNumber":"+8613800138000","status":"ACTIVE"}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        assertFailsWith<UserProfileDependencyException> { service.findById(42) }
        server.verify()
    }

    @Test
    fun `invalid verified phone response is propagated as a dependency failure`() {
        server
            .expect(requestTo("http://user-service/internal/api/users/42/profile"))
            .andRespond(
                withSuccess(
                    """{"userId":42,"nickname":"buyer","phoneNumber":"not-a-phone","status":"ACTIVE"}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        assertFailsWith<UserProfileDependencyException> { service.findById(42) }
        server.verify()
    }

    @Test
    fun `profile for a different user is rejected as a dependency failure`() {
        server
            .expect(requestTo("http://user-service/internal/api/users/42/profile"))
            .andRespond(
                withSuccess(
                    """{"userId":7,"nickname":"other","phoneNumber":"+8613900139000","status":"ACTIVE"}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        assertFailsWith<UserProfileDependencyException> { service.findById(42) }
        server.verify()
    }
}
