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
package com.jstore

// import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@EnableJpaAuditing
@EntityScan(basePackages = ["com.jstore"])
@EnableJpaRepositories(
    basePackages = ["com.jstore"],
    excludeFilters =
        [
            ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = ["com\\.jstore\\.outbox\\.spring\\.persistence\\..*"],
            )
        ],
)
@SpringBootApplication
// @EnableDiscoveryClient
@EnableConfigurationProperties
@EnableScheduling
class JStoreOrderBootApplication

fun main(args: Array<String>) {
    SpringApplication.run(JStoreOrderBootApplication::class.java, *args)
}
