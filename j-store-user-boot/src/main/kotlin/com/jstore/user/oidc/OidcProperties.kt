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

import java.net.URI
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("jstore.oidc")
data class OidcProperties(
    val issuer: String = "",
    val resourceAudience: String = "j-store-api",
    val jwkSetLocation: String = "",
    val activeKeyId: String = "",
    val clients: List<OidcClientProperties> = emptyList(),
    val wechat: WechatProperties = WechatProperties(),
) {
    fun validate(development: Boolean) {
        val uri = URI(issuer)
        require(
            uri.isAbsolute &&
                uri.host != null &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null &&
                !issuer.endsWith('/')
        ) {
            "Configure a canonical OIDC issuer without trailing slash"
        }
        require(
            uri.scheme == "https" ||
                (development && uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1"))
        ) {
            "OIDC issuer requires HTTPS"
        }
        require(jwkSetLocation.isNotBlank() && activeKeyId.isNotBlank() && clients.isNotEmpty()) {
            "OIDC signing keys and clients must be explicitly configured"
        }
        clients.forEach { client ->
            client.validate()
            if (!development)
                require(
                    (client.redirects + client.postLogoutRedirects + client.corsOrigins).all {
                        URI(it).scheme == "https"
                    }
                )
        }
        require(resourceAudience.isNotBlank())
        require(clients.map { it.id }.toSet().size == clients.size)
        if (wechat.enabled) {
            require(wechat.appId.isNotBlank() && wechat.secret.isNotBlank())
            require(wechat.callbackUri == "$issuer/login/wechat/callback")
        }
    }
}

data class OidcClientProperties(
    val id: String = "",
    val name: String = "",
    val secretHash: String = "",
    val redirects: List<String> = emptyList(),
    val postLogoutRedirects: List<String> = emptyList(),
    val scopes: Set<String> = setOf("openid", "profile"),
    val audiences: Set<String> = setOf("j-store-api"),
    val corsOrigins: List<String> = emptyList(),
) {
    val confidential
        get() = secretHash.isNotBlank()

    fun validate() {
        require(
            id.isNotBlank() && id.length <= 100 && redirects.isNotEmpty() && audiences.isNotEmpty()
        )
        require(
            scopes.contains("openid") &&
                setOf("openid", "profile", "phone", "offline_access").containsAll(scopes)
        )
        require(confidential || "offline_access" !in scopes) {
            "Public clients cannot request offline access"
        }
        require(
            !confidential ||
                secretHash.matches(Regex("\\{bcrypt}\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}"))
        ) {
            "Client secret must be a BCrypt digest"
        }
        (redirects + postLogoutRedirects).forEach { value ->
            val uri = URI(value)
            require(
                '*' !in value &&
                    uri.isAbsolute &&
                    uri.host != null &&
                    uri.userInfo == null &&
                    uri.fragment == null
            )
            require(
                uri.scheme == "https" ||
                    (uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1"))
            )
        }
        corsOrigins.forEach { value ->
            val uri = URI(value)
            require(
                '*' !in value &&
                    uri.host != null &&
                    uri.userInfo == null &&
                    uri.query == null &&
                    uri.fragment == null &&
                    uri.path.isNullOrEmpty()
            )
            require(
                uri.scheme == "https" ||
                    (uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1"))
            )
        }
    }
}

data class WechatProperties(
    val enabled: Boolean = false,
    val appId: String = "",
    val secret: String = "",
    val callbackUri: String = "",
)
