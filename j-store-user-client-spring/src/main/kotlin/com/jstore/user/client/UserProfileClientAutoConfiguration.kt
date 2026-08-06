package com.jstore.user.client

import com.jstore.user.api.UserProfileQueryService
import java.net.URI
import java.time.Duration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@ConfigurationProperties("jstore.user-query.remote")
class UserProfileRemoteProperties {
    var baseUrl: String = ""
    var token: String = ""
    var connectTimeout: Duration = Duration.ofSeconds(2)
    var readTimeout: Duration = Duration.ofSeconds(3)

    fun validate() {
        val uri = runCatching { URI(baseUrl) }.getOrNull()
        require(uri != null && uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) {
            "jstore.user-query.remote.base-url must be an absolute HTTP(S) URL"
        }
        require(token.length >= 32) {
            "jstore.user-query.remote.token must contain at least 32 characters"
        }
        require(!connectTimeout.isNegative && !connectTimeout.isZero) {
            "jstore.user-query.remote.connect-timeout must be positive"
        }
        require(!readTimeout.isNegative && !readTimeout.isZero) {
            "jstore.user-query.remote.read-timeout must be positive"
        }
    }
}

@AutoConfiguration
@ConditionalOnProperty(prefix = "jstore.user-query", name = ["mode"], havingValue = "remote")
@EnableConfigurationProperties(UserProfileRemoteProperties::class)
class UserProfileClientAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(UserProfileQueryService::class)
    fun remoteUserProfileQueryService(
        properties: UserProfileRemoteProperties
    ): HttpUserProfileQueryService {
        properties.validate()
        val requestFactory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(properties.connectTimeout)
                setReadTimeout(properties.readTimeout)
            }
        val restClient =
            RestClient.builder()
                .baseUrl(properties.baseUrl.removeSuffix("/"))
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${properties.token}")
                .build()
        return HttpUserProfileQueryService(restClient)
    }
}
