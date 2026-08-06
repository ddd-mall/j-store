package com.jstore.order.domain.order

import com.jstore.common.errors.BusinessError
import com.jstore.common.geo.*
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.GoodsInfo
import com.jstore.order.acl.GoodsService
import com.jstore.order.domain.order.command.OrderCreateCMD
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.Locale

/**
 * OrderFactory 单元测试
 * - countryCode 显式透传给 GeoAddressService，不再有默认兜底
 * - GeoAddressService 查询失败时 Factory 返回 Failure
 *
 * **Validates: Requirements 2.2, 2.5**
 */
class OrderFactoryUnitTest :
    FunSpec({
        val sampleAddress =
            I18nGeoAddress(
                countryCode = CountryCode.CN,
                components =
                    listOf(
                        AddressComponent(
                            code = "110000",
                            level = DivisionLevel(1, "省"),
                            names = mapOf(Locale.SIMPLIFIED_CHINESE to "北京市"),
                            defaultLocale = Locale.SIMPLIFIED_CHINESE,
                        )
                    ),
            )

        val stubGoodsService =
            object : GoodsService {
                override fun queryGoods(goodsId: List<GoodsId>): List<GoodsInfo> {
                    return goodsId.map {
                        GoodsInfo(
                            id = it,
                            merchantId = 7,
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

        test(
            "OrderFactory should pass RecipientInfoCMD.countryCode through to GeoAddressService as-is"
        ) {
            var capturedCountryCode: String? = null

            val capturingGeoService =
                object : GeoAddressService {
                    override fun getByCode(
                        countryCode: String,
                        addressCode: String,
                    ): Result<I18nGeoAddress, BusinessError> {
                        capturedCountryCode = countryCode
                        return Success(sampleAddress)
                    }
                }

            val factory = OrderFactoryImpl(snowFlakSequence, stubGoodsService, capturingGeoService)

            val cmd =
                OrderCreateCMD(
                    buyerUid = 1L,
                    merchantId = 7,
                    buyerPhone = "+8613800138000",
                    buyerName = "买家",
                    recipientInfo =
                        OrderCreateCMD.RecipientInfoCMD(
                            consigneeName = "张三",
                            countryCode = "CN",
                            consigneeContractInfo =
                                OrderCreateCMD.ContractInfoCMD(
                                    phoneNumber = PhoneNumber("+8613900139000"),
                                    emailAddress = null,
                                ),
                            shippingDistrictCode = "110000",
                            shippingDetailAddress = "朝阳区三里屯",
                        ),
                    items =
                        listOf(
                            OrderCreateCMD.OrderItemCMD(
                                spuId = 1,
                                skuId = 1,
                                quantity = 1,
                                snapshotVersion = 1L,
                            )
                        ),
                )

            val result = factory.create(cmd)

            result.shouldBeInstanceOf<Success<Order>>()
            result.value.tradeStatus shouldBe TradeStatus.CREATED
            result.value.paymentStatus shouldBe PaymentStatus.UNPAID
            result.value.fulfillmentStatus shouldBe FulfillmentStatus.UNFULFILLED
            capturedCountryCode shouldBe "CN"
        }

        test(
            "When GeoAddressService.getByCode() returns Failure, OrderFactory.create() should return Failure"
        ) {
            val geoError = BusinessError("地址查询失败", "Geo.NotFound", 404)

            val failingGeoService =
                object : GeoAddressService {
                    override fun getByCode(
                        countryCode: String,
                        addressCode: String,
                    ): Result<I18nGeoAddress, BusinessError> {
                        return Failure(geoError)
                    }
                }

            val factory = OrderFactoryImpl(snowFlakSequence, stubGoodsService, failingGeoService)

            val cmd =
                OrderCreateCMD(
                    buyerUid = 1L,
                    merchantId = 7,
                    buyerPhone = "+8613800138000",
                    buyerName = "买家",
                    recipientInfo =
                        OrderCreateCMD.RecipientInfoCMD(
                            consigneeName = "张三",
                            countryCode = "CN",
                            consigneeContractInfo =
                                OrderCreateCMD.ContractInfoCMD(
                                    phoneNumber = PhoneNumber("+8613900139000"),
                                    emailAddress = null,
                                ),
                            shippingDistrictCode = "110000",
                            shippingDetailAddress = "朝阳区三里屯",
                        ),
                    items =
                        listOf(
                            OrderCreateCMD.OrderItemCMD(
                                spuId = 1,
                                skuId = 1,
                                quantity = 1,
                                snapshotVersion = 1L,
                            )
                        ),
                )

            val result = factory.create(cmd)

            result.shouldBeInstanceOf<Failure<BusinessError>>()
            result.error shouldBe geoError
        }
    })
