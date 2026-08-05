package com.jstore.shop.config

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.shop.domain.merchant.MerchantMembershipRepository
import com.jstore.shop.domain.merchant.MerchantRepository
import com.jstore.shop.service.MerchantAuthorizationService
import com.jstore.shop.service.MerchantIdGenerator
import com.jstore.shop.service.MerchantService
import com.jstore.shop.service.UserAccountLookup
import com.jstore.user.domain.useraccount.UserAccountRepository
import com.jstore.user.domain.useraccount.UserId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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
    fun merchantService(
        idGenerator: MerchantIdGenerator,
        merchantRepository: MerchantRepository,
        membershipRepository: MerchantMembershipRepository,
        userAccountLookup: UserAccountLookup,
    ) = MerchantService(idGenerator, merchantRepository, membershipRepository, userAccountLookup)
}
