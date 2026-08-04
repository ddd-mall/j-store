package com.jstore.order.domain.order

import com.jstore.common.geo.*
import com.jstore.order.domain.order.persistence.OrderItemPO
import com.jstore.order.domain.order.persistence.OrderPO
import com.jstore.order.domain.order.persistence.RecipientInfoPO
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Locale

/**
 * Feature: order-consignee-info, Property 5: 历史数据反序列化默认值
 *
 * For any 合法的 RecipientInfoPO JSON，当 consigneeName 字段缺失或为 null 时， 反序列化后重建的
 * ShippingInfo.consigneeName 应为空字符串 ""； 当 consigneePhone 和 consigneeEmail 均缺失或为 null 时， 重建的
 * ContractInfo 的 phoneNumber 和 email 均应为 null。
 *
 * **Validates: Requirements 6.1, 6.2**
 */
class RecipientInfoPOBackwardCompatPropertyTest :
    FunSpec({

        // Generator for non-blank strings
        val nonBlankStringArb: Arb<String> = Arb.string(1..30).filter { it.isNotBlank() }

        // Generator for 6-digit code strings
        val digitCodeArb: Arb<String> = Arb.int(100000..999999).map { it.toString() }

        // Generator for I18nGeoAddress (always valid — historical data always has an address)
        val i18nGeoAddressArb: Arb<I18nGeoAddress> =
            Arb.bind(
                digitCodeArb,
                nonBlankStringArb,
            ) { code, name ->
                I18nGeoAddress(
                    countryCode = CountryCode.CN,
                    components =
                        listOf(
                            AddressComponent(
                                code = code,
                                level = DivisionLevel(1, "省"),
                                names = mapOf(Locale.SIMPLIFIED_CHINESE to name),
                                defaultLocale = Locale.SIMPLIFIED_CHINESE,
                            )
                        ),
                )
            }

        // Generator for optional detail address
        val optionalDetailAddressArb: Arb<String?> =
            Arb.choice(
                Arb.constant(null as String?),
                nonBlankStringArb,
            )

        val converter = OrderRepositoryImpl.Converter
        val now = LocalDateTime.now()

        test("consigneeName null defaults to empty string in ShippingInfo") {
            checkAll(100, i18nGeoAddressArb, digitCodeArb, optionalDetailAddressArb) {
                address,
                districtCode,
                detailAddress ->
                // Simulate historical data: consigneeName is null
                val historicalPO =
                    RecipientInfoPO(
                        consigneeName = null,
                        consigneePhone = "13800138000",
                        consigneeEmail = "test@test.com",
                        countryCode = address.countryCode.value,
                        districtCode = districtCode,
                        shippingAddress = address,
                        detailAddress = detailAddress,
                    )

                val orderPO =
                    OrderPO(
                        id = 1L,
                        merchantId = 1L,
                        buyerUid = 1L,
                        recipientInfo = historicalPO,
                        itemsSubtotal = BigDecimal.valueOf(100),
                        payableAmount = BigDecimal.valueOf(100),
                        createTime = now,
                        updateTime = now,
                        items = mutableListOf(testItemPO()),
                    )

                val order = converter.toDomain(orderPO)
                order.recipientInfo.name shouldBe ""
            }
        }

        test(
            "consigneePhone and consigneeEmail both null defaults to ContractInfo with null phoneNumber and null email"
        ) {
            checkAll(
                100,
                i18nGeoAddressArb,
                digitCodeArb,
                nonBlankStringArb,
                optionalDetailAddressArb,
            ) { address, districtCode, consigneeName, detailAddress ->
                // Simulate historical data: both phone and email are null
                val historicalPO =
                    RecipientInfoPO(
                        consigneeName = consigneeName,
                        consigneePhone = null,
                        consigneeEmail = null,
                        countryCode = address.countryCode.value,
                        districtCode = districtCode,
                        shippingAddress = address,
                        detailAddress = detailAddress,
                    )

                val orderPO =
                    OrderPO(
                        id = 1L,
                        merchantId = 1L,
                        buyerUid = 1L,
                        recipientInfo = historicalPO,
                        itemsSubtotal = BigDecimal.valueOf(100),
                        payableAmount = BigDecimal.valueOf(100),
                        createTime = now,
                        updateTime = now,
                        items = mutableListOf(testItemPO()),
                    )

                val order = converter.toDomain(orderPO)
                order.recipientInfo.contractInfo.phoneNumber shouldBe null
                order.recipientInfo.contractInfo.email shouldBe null
            }
        }
    })

private fun testItemPO() =
    OrderItemPO(
        id = 1,
        orderId = 1,
        skuId = 1,
        spuId = 1,
        goodsName = "test",
        skuDescription = "test",
        quantity = 1,
        unitPrice = BigDecimal.valueOf(100),
    )
