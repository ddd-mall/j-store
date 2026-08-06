package com.jstore.user.config

import com.jstore.common.properties.PhoneNumber
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

@ExtendWith(OutputCaptureExtension::class)
class DevelopmentPhoneVerificationSenderTest {
    @Test
    fun `development sender does not log verification code or complete phone number`(
        output: CapturedOutput
    ) {
        DevelopmentPhoneVerificationSender()
            .send(
                PhoneNumber("+8613800138000"),
                "739241",
            )

        assertFalse(output.out.contains("739241"))
        assertFalse(output.out.contains("+8613800138000"))
        assertTrue(output.out.contains("+86138****8000"))
    }
}
