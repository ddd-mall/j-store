package com.jstore.order.config
import org.junit.jupiter.api.Test
import kotlin.test.*
class OrderMerchantConfigurationTest{
 @Test fun `positive merchant is accepted`(){assertEquals(7,OrderMerchantProperties(7).merchantId)}
 @Test fun `missing zero and negative merchant fail`(){listOf<Long?>(null,0,-1).forEach{assertFailsWith<IllegalArgumentException>{OrderMerchantProperties(it)}}}
}
