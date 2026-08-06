package com.jstore.shop.config

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.shop.domain.merchant.MerchantMembershipRepository
import com.jstore.shop.domain.merchant.MerchantRepository
import com.jstore.shop.service.MerchantAuthorizationService
import com.jstore.shop.service.MerchantIdGenerator
import com.jstore.shop.service.MerchantService
import com.jstore.shop.service.MerchantUseCase
import com.jstore.shop.service.UserAccountLookup
import com.jstore.user.api.UserProfileQueryService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class MerchantBootConfiguration {
    @Bean
    fun merchantIdGenerator(sequence: SnowFlakSequence) = MerchantIdGenerator(sequence::nextId)

    @Bean
    fun merchantUserAccountLookup(userProfiles: UserProfileQueryService) =
        UserAccountLookup { userId ->
            userProfiles.findById(userId) != null
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
