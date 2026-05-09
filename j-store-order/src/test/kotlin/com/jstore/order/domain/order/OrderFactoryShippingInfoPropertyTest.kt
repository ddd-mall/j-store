package com.jstore.order.domain.order

import com.jstore.common.errors.BusinessError
import com.jstore.common.geo.*
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.properties.Price
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.GoodsInfo
import com.jstore.order.acl.GoodsService
import com.jstore.order.domain.order.command.OrderCreateCMD
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import java.util.Locale

/**
 * Feature: order-recipient-info, Property 1: 工厂正确组装 ShippingInfo
 *
 * For any 合法的 RecipientInfoCMD（含随机 consigneeName、ContractInfoCMD、countryCode、districtCode、detailAddress），
 * 当 GeoAddressService 返回成功的 I18nGeoAddress 时，OrderFactory 创建的 Order 的 shippingInfo 应满足：
 * - consigneeName 等于 CMD 中的 consigneeName
 * - consigneeContractInfo.phoneNumber 等于 CMD 中的 ContractInfo.phoneNumber
 * - consigneeContractInfo.email 等于 CMD 中的 consigneeContractInfo.emailAddress
 * - shippingAddress 等于 GeoAddressService 返回的 I18nGeoAddress
 * - shippingDetailAddress 等于 CMD 中的 shippingDetailAddress
 *
 * **Validates: Requirements 2.1, 2.3, 2.4, 7.4**
 */
class OrderFactoryShippingInfoPropertyTest : FunSpec({

    // Generator for valid Chinese phone numbers (11 digits starting with 13x)
    val validPhoneArb: Arb<PhoneNumber> = Arb.int(0..99999999).map { num ->
        PhoneNumber("13${num.toString().padStart(9, '0')}")
    }

    // Generator for non-blank strings
    val nonBlankStringArb: Arb<String> = Arb.string(1..30).filter { it.isNotBlank() }

    // Generator for optional email addresses
    val optionalEmailArb: Arb<String?> = Arb.choice(
        Arb.constant(null as String?),
        nonBlankStringArb.map { "${it.take(10)}@test.com" }
    )

    // Generator for valid ContractInfoCMD (at least one of phone or email must be non-null)
    val validContractInfoCMDArb: Arb<OrderCreateCMD.ContractInfoCMD> = Arb.bind(
        Arb.boolean(),
        validPhoneArb,
        optionalEmailArb,
    ) { hasPhone, phone, email ->
        if (hasPhone) {
            OrderCreateCMD.ContractInfoCMD(phoneNumber = phone, emailAddress = email)
        } else {
            // If no phone, email must be non-null
            OrderCreateCMD.ContractInfoCMD(phoneNumber = null, emailAddress = email ?: "fallback@test.com")
        }
    }

    // Generator for 6-digit code strings (e.g. district codes)
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

    // Generator for valid RecipientInfoCMD
    val validRecipientInfoCMDArb: Arb<OrderCreateCMD.RecipientInfoCMD> = Arb.bind(
        nonBlankStringArb,
        validContractInfoCMDArb,
        digitCodeArb,
        nonBlankStringArb,
    ) { consigneeName, contractInfo, districtCode, detailAddress ->
        OrderCreateCMD.RecipientInfoCMD(
            consigneeName = consigneeName,
            countryCode = "CN",
            consigneeContractInfo = contractInfo,
            shippingDistrictCode = districtCode,
            shippingDetailAddress = detailAddress,
        )
    }


    test("OrderFactory.create() correctly assembles ShippingInfo from RecipientInfoCMD") {
        checkAll(100, validRecipientInfoCMDArb, i18nGeoAddressArb) { recipientInfoCMD, expectedAddress ->
            // Stub GeoAddressService to return the generated address
            val geoAddressService = object : GeoAddressService {
                override fun getByCode(countryCode: String, addressCode: String): Result<I18nGeoAddress, BusinessError> {
                    return Success(expectedAddress)
                }
            }

            // Stub GoodsService to return a valid goods item
            val goodsService = object : GoodsService {
                override fun queryGoods(goodsId: List<GoodsId>): List<GoodsInfo> {
                    return goodsId.map {
                        GoodsInfo(
                            id = it,
                            snapshotVersion = 1L,
                            spuName = "测试商品",
                            skuName = "默认规格",
                            attributes = emptyList(),
                            price = Price.ofFen(100),
                        )
                    }
                }
            }

            val snowFlakSequence = SnowFlakSequence(1, 1)

            val factory = OrderFactoryImpl(snowFlakSequence, goodsService, geoAddressService)

            val cmd = OrderCreateCMD(
                buyerUid = 1L,
                buyerPhone = "13800138000",
                buyerName = "买家",
                recipientInfo = recipientInfoCMD,
                items = listOf(OrderCreateCMD.OrderItemCMD(spuId = 1, skuId = 1, quantity = 1, snapshotVersion = 1L)),
            )

            val result = factory.create(cmd)

            result.shouldBeInstanceOf<Success<Order>>()
            val order = result.value
            val shippingInfo = order.recipientInfo

            // Verify all ShippingInfo fields match CMD input
            shippingInfo.name shouldBe recipientInfoCMD.consigneeName
            shippingInfo.contractInfo.phoneNumber shouldBe recipientInfoCMD.consigneeContractInfo.phoneNumber
            shippingInfo.contractInfo.email shouldBe recipientInfoCMD.consigneeContractInfo.emailAddress
            shippingInfo.shippingAddress shouldBe expectedAddress
            shippingInfo.shippingDetailAddress shouldBe recipientInfoCMD.shippingDetailAddress
        }
    }
})
