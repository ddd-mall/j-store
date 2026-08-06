package com.jstore.user.client

import com.jstore.user.api.UserProfileQueryService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class UserProfileClientAutoConfigurationTest {
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(UserProfileClientAutoConfiguration::class.java)
            )

    @Test
    fun `local mode does not create a remote query service`() {
        runner.run { context ->
            assertThat(context).doesNotHaveBean(UserProfileQueryService::class.java)
        }
    }

    @Test
    fun `remote mode creates the HTTP query service when configuration is complete`() {
        runner
            .withPropertyValues(
                "jstore.user-query.mode=remote",
                "jstore.user-query.remote.base-url=http://user-service",
                "jstore.user-query.remote.token=${"a".repeat(32)}",
            )
            .run { context ->
                assertThat(context).hasSingleBean(UserProfileQueryService::class.java)
                assertThat(context).hasSingleBean(HttpUserProfileQueryService::class.java)
            }
    }

    @Test
    fun `remote mode fails fast when service credentials are absent`() {
        runner
            .withPropertyValues(
                "jstore.user-query.mode=remote",
                "jstore.user-query.remote.base-url=http://user-service",
            )
            .run { context -> assertThat(context).hasFailed() }
    }
}
