package com.jstore.order.domain.order

import com.jstore.common.geo.*
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.properties.Price
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import java.time.LocalDateTime
import java.util.Locale

/**
 * Feature: order-recipient-info, Property 4: ShippingInfo ↔ RecipientInfoPO 序列化往返
 *
 * For any 合法的 ShippingInfo（含随机 consigneeName、ContractInfo、I18nGeoAddress、detailAddress），
 * 通过 Converter 转换为 RecipientInfoPO 再转换回 ShippingInfo，应产生与原始对象在所有字段上等价的结果。
 *
 * **Validates: Requirements 4.4, 4.5, 6.3**
 */
class RecipientInfoPORoundTripPropertyTest : FunSpec({

    // Generator for valid Chinese phone numbers (11 digits starting with 13x)
    val validPhoneArb: Arb<PhoneNumber> = Arb.int(0..99999999).map { num ->
        PhoneNumber("13${num.toString().padStart(9, '0')}")
    }

    // Generator for non-blank strings
    val nonBlankStringArb: Arb<String> = Arb.string(1..30).filter { it.isNotBlank() }

    // Generator for 6-digit code strings
    val digitCodeArb: Arb<String> = Arb.int(100000..999999).map { it.toString() }

    // Generator for I18nGeoAddress
    val i18nGeoAddressArb: Arb<I18nGeoAddress> = Arb.bind(
        digitCodeArb,
        nonBlankStringArb,
    ) { code, name ->
        I18nGeoAddress(
            countryCode = CountryCode.CN,
            components = listOf(
                AddressComponent(
                    code = code,
                    level = DivisionLevel(1, "省"),
                    names = mapOf(Locale.SIMPLIFIED_CHINESE to name),
                    defaultLocale = Locale.SIMPLIFIED_CHINESE,
                )
            )
        )
    }

    // Generator for valid ContractInfo (at least one of phone or email non-null)
    val validContractInfoArb: Arb<ContractInfo> = Arb.bind(
        Arb.boolean(),
        validPhoneArb,
        Arb.choice(
            Arb.constant(null as String?),
            nonBlankStringArb.map { "${it.take(10)}@test.com" }
        ),
    ) { hasPhone, phone, email ->
        if (hasPhone) {
            ContractInfo(email = email, phoneNumber = phone)
        } else {
            ContractInfo(email = email ?: "fallback@test.com", phoneNumber = null)
        }
    }

    // Generator for optional detail address
    val optionalDetailAddressArb: Arb<String?> = Arb.choice(
        Arb.constant(null as String?),
        nonBlankStringArb,
    )

    // Generator for valid ShippingInfo
    val recipientInfoArb: Arb<RecipientInfo> = Arb.bind(
        nonBlankStringArb,
        validContractInfoArb,
        i18nGeoAddressArb,
        optionalDetailAddressArb,
    ) { consigneeName, contractInfo, address, detailAddress ->
        RecipientInfo(
            name = consigneeName,
            contractInfo = contractInfo,
            shippingAddress = address,
            shippingDetailAddress = detailAddress,
        )
    }

    test("ShippingInfo round-trips through RecipientInfoPO via Converter toPO/toDomain") {
        val converter = OrderRepositoryImpl.Converter
        val now = LocalDateTime.now()

        checkAll(100, recipientInfoArb) { originalShippingInfo ->
            // Build a minimal Order with the generated ShippingInfo
            val order: Order = OrderImpl(
                id = OrderId(1L),
                buyerInfo = UserInfo(uid = 1L, phoneNumber = null, userName = "test"),
                _items = mutableListOf(
                    OrderItemImpl(
                        id = OrderItemId(1L),
                        skuId = 1L,
                        spuId = 1L,
                        goodsName = "test",
                        skuDescription = "test",
                        quantity = 1,
                        unitPrice = Price.ofFen(100),
                        status = OrderItemStatus.NONE,
                    )
                ),
                recipientInfo = originalShippingInfo,
                _status = OrderStatus.PENDING_STOCK,
                totalAmount = Price.ofFen(100),
                _actualPay = Price.ofFen(100),
                createTime = now,
                _updateTime = now,
            )

            // Convert to PO and back
            val po = converter.toPO(order)
            val restored = converter.toDomain(po)
            val restoredShippingInfo = restored.recipientInfo

            // Verify all fields are equivalent
            restoredShippingInfo.name shouldBe originalShippingInfo.name
            restoredShippingInfo.contractInfo.phoneNumber shouldBe originalShippingInfo.contractInfo.phoneNumber
            restoredShippingInfo.contractInfo.email shouldBe originalShippingInfo.contractInfo.email
            restoredShippingInfo.shippingAddress shouldBe originalShippingInfo.shippingAddress
            restoredShippingInfo.shippingDetailAddress shouldBe originalShippingInfo.shippingDetailAddress
        }
    }
})
