package com.jstore.authentication.spring

import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.user.domain.useraccount.TokenProvider
import com.jstore.user.domain.useraccount.TokenStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner

/** 集成测试：验证 AuthenticationAutoConfiguration 的条件激活行为。 _需求: 7.1, 7.3, 7.4_ */
class AuthenticationAutoConfigurationTest {

    private val contextRunner =
        WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuthenticationAutoConfiguration::class.java))

    @Test
    fun `auto-configuration activates when both TokenProvider and TokenStore beans are present`() {
        contextRunner
            .withBean(TokenProvider::class.java, { mock() })
            .withBean(TokenStore::class.java, { mock() })
            .withBean(ObjectMapper::class.java, { ObjectMapper() })
            .run { context ->
                assertThat(context).hasSingleBean(AuthenticationInterceptor::class.java)
                assertThat(context).hasSingleBean(CurrentUserIdArgumentResolver::class.java)
            }
    }

    @Test
    fun `auto-configuration does not activate when TokenProvider is missing`() {
        contextRunner.withBean(TokenStore::class.java, { mock() }).run { context ->
            assertThat(context).doesNotHaveBean(AuthenticationInterceptor::class.java)
            assertThat(context).doesNotHaveBean(CurrentUserIdArgumentResolver::class.java)
        }
    }

    @Test
    fun `auto-configuration does not activate when TokenStore is missing`() {
        contextRunner.withBean(TokenProvider::class.java, { mock() }).run { context ->
            assertThat(context).doesNotHaveBean(AuthenticationInterceptor::class.java)
            assertThat(context).doesNotHaveBean(CurrentUserIdArgumentResolver::class.java)
        }
    }

    @Test
    fun `auto-configuration does not activate when both beans are missing`() {
        contextRunner.run { context ->
            assertThat(context).doesNotHaveBean(AuthenticationInterceptor::class.java)
            assertThat(context).doesNotHaveBean(CurrentUserIdArgumentResolver::class.java)
        }
    }
}
