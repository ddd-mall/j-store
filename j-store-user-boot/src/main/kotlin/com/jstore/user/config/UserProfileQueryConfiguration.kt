package com.jstore.user.config

import com.jstore.user.api.UserProfileQueryService
import com.jstore.user.domain.useraccount.UserAccountRepository
import com.jstore.user.service.UserProfileReader
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class UserProfileQueryConfiguration {
    @Bean
    fun userProfileReader(repository: UserAccountRepository): UserProfileReader =
        UserProfileReader(repository)

    @Bean
    @ConditionalOnProperty(
        prefix = "jstore.user-query",
        name = ["mode"],
        havingValue = "local",
        matchIfMissing = true,
    )
    fun localUserProfileQueryService(reader: UserProfileReader): UserProfileQueryService =
        UserProfileQueryService(reader::findById)
}
