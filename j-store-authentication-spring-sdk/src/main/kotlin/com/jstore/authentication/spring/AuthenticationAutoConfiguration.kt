package com.jstore.authentication.spring

import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.authentication.config.AuthenticationConfigurer
import com.jstore.user.domain.useraccount.TokenProvider
import com.jstore.user.domain.useraccount.TokenStore
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(TokenProvider::class, TokenStore::class)
class AuthenticationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun authenticationInterceptor(
        tokenProvider: TokenProvider,
        tokenStore: TokenStore,
        configurers: List<AuthenticationConfigurer>,
        objectMapper: ObjectMapper,
    ): AuthenticationInterceptor {
        return AuthenticationInterceptor(tokenProvider, tokenStore, configurers, objectMapper)
    }

    @Bean
    @ConditionalOnMissingBean
    fun currentUserIdArgumentResolver(): CurrentUserIdArgumentResolver {
        return CurrentUserIdArgumentResolver()
    }

    @Bean
    fun authenticationWebMvcConfigurer(
        interceptor: AuthenticationInterceptor,
        resolver: CurrentUserIdArgumentResolver,
    ): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(interceptor).addPathPatterns("/**")
            }

            override fun addArgumentResolvers(
                resolvers: MutableList<HandlerMethodArgumentResolver>
            ) {
                resolvers.add(resolver)
            }
        }
    }
}
