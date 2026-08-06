package com.jstore.user.controller

import com.jstore.user.service.UserProfileReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

class InternalUserProfileEndpointConfigurationTest {
    private val runner =
        WebApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(PropertyPlaceholderAutoConfiguration::class.java)
            )
            .withBean(UserProfileReader::class.java, { mock() })
            .withUserConfiguration(TestConfiguration::class.java)

    @Test
    fun `internal endpoint is disabled by default`() {
        runner.run { context ->
            assertThat(context).doesNotHaveBean(InternalUserProfileController::class.java)
        }
    }

    @Test
    fun `enabled endpoint fails fast without its service credential`() {
        runner.withPropertyValues("jstore.user-query.server.enabled=true").run { context ->
            assertThat(context).hasFailed()
        }
    }

    @Test
    fun `enabled endpoint is created with a valid service credential`() {
        runner
            .withPropertyValues(
                "jstore.user-query.server.enabled=true",
                "jstore.user-query.server.token=${"a".repeat(32)}",
            )
            .run { context ->
                assertThat(context).hasSingleBean(InternalUserProfileController::class.java)
            }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(InternalUserProfileController::class)
    private class TestConfiguration
}
