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
