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
package com.jstore.shop.config

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.shop.domain.merchant.MerchantMembershipRepository
import com.jstore.shop.domain.merchant.MerchantRepository
import com.jstore.shop.service.MerchantAuthorizationService
import com.jstore.shop.service.MerchantIdGenerator
import com.jstore.shop.service.MerchantService
import com.jstore.shop.service.MerchantUseCase
import com.jstore.shop.service.UserAccountLookup
import com.jstore.user.domain.useraccount.UserAccountRepository
import com.jstore.user.domain.useraccount.UserId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class MerchantBootConfiguration {
    @Bean
    fun merchantIdGenerator(sequence: SnowFlakSequence) = MerchantIdGenerator(sequence::nextId)

    @Bean
    fun merchantUserAccountLookup(userAccountRepository: UserAccountRepository) =
        UserAccountLookup { userId ->
            userAccountRepository.existsById(UserId(userId))
        }

    @Bean
    fun merchantAuthorizationService(
        merchantRepository: MerchantRepository,
        membershipRepository: MerchantMembershipRepository,
    ) = MerchantAuthorizationService(merchantRepository, membershipRepository)

    @Bean
    fun merchantApplicationService(
        idGenerator: MerchantIdGenerator,
        merchantRepository: MerchantRepository,
        membershipRepository: MerchantMembershipRepository,
        userAccountLookup: UserAccountLookup,
    ) = MerchantService(idGenerator, merchantRepository, membershipRepository, userAccountLookup)

    @Bean
    @Primary
    fun transactionalMerchantUseCase(
        merchantApplicationService: MerchantService,
        transactionManager: PlatformTransactionManager,
    ): MerchantUseCase =
        TransactionalMerchantUseCase(merchantApplicationService, transactionManager)
}
