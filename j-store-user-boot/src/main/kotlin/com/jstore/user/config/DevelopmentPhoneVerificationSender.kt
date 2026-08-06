package com.jstore.user.config

import com.jstore.common.properties.PhoneNumber
import com.jstore.user.domain.useraccount.PhoneVerificationCodeSender
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("local", "dev")
class DevelopmentPhoneVerificationSender : PhoneVerificationCodeSender {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun send(phoneNumber: PhoneNumber, code: String) {
        val nationalNumber = phoneNumber.nationalNumber
        val maskedNumber =
            if (nationalNumber.length > 7) {
                "+${phoneNumber.countryCallingCode}${nationalNumber.take(3)}****${nationalNumber.takeLast(4)}"
            } else {
                "+${phoneNumber.countryCallingCode}****"
            }
        logger.info("Development phone verification code issued for {}", maskedNumber)
    }
}
