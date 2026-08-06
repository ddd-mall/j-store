package com.jstore.user.config

import com.jstore.user.api.UserProfileQueryService
import com.jstore.user.client.HttpUserProfileQueryService
import com.jstore.user.client.UserProfileClientAutoConfiguration
import com.jstore.user.domain.useraccount.UserAccountRepository
import com.jstore.user.service.UserProfileReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class UserProfileQueryDeploymentConfigurationTest {
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(UserProfileClientAutoConfiguration::class.java)
            )
            .withUserConfiguration(UserProfileQueryConfiguration::class.java)
            .withBean(UserAccountRepository::class.java, { mock() })

    @Test
    fun `default local mode exposes exactly one in-process consumer service`() {
        runner.run { context ->
            assertThat(context).hasSingleBean(UserProfileQueryService::class.java)
            assertThat(context).doesNotHaveBean(HttpUserProfileQueryService::class.java)
        }
    }

    @Test
    fun `remote mode replaces the local consumer with exactly one HTTP service`() {
        runner
            .withPropertyValues(
                "jstore.user-query.mode=remote",
                "jstore.user-query.remote.base-url=http://user-service",
                "jstore.user-query.remote.token=${"a".repeat(32)}",
            )
            .run { context ->
                assertThat(context).hasSingleBean(UserProfileQueryService::class.java)
                assertThat(context).hasSingleBean(HttpUserProfileQueryService::class.java)
                assertThat(context).hasSingleBean(UserProfileReader::class.java)
            }
    }

    @Test
    fun `remote mode fails fast instead of falling back when configuration is incomplete`() {
        runner
            .withPropertyValues(
                "jstore.user-query.mode=remote",
                "jstore.user-query.remote.base-url=http://user-service",
            )
            .run { context -> assertThat(context).hasFailed() }
    }
}
