/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.order.domain.order

import com.jstore.common.errors.BusinessError
import com.jstore.common.geo.GeoAddressService
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.fold
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.GoodsService
import com.jstore.order.acl.OfferService
import com.jstore.order.domain.order.command.OrderCreateCMD

/** 订单工厂 负责组装一个合法的初始状态的 Order 聚合根 创建过程需要跨上下文查询（商品价格、地址信息），这些依赖不应注入到聚合根 */
interface OrderFactory {
    fun create(cmd: OrderCreateCMD, buyerInfo: UserInfo): Result<Order, BusinessError>
}

class OrderFactoryImpl(
    private val snowFlakSequence: SnowFlakSequence,
    private val goodsService: GoodsService,
    private val geoAddressService: GeoAddressService,
    private val offerService: OfferService,
) : OrderFactory {

    override fun create(cmd: OrderCreateCMD, buyerInfo: UserInfo): Result<Order, BusinessError> {
        if (buyerInfo.uid != cmd.buyerUid) return Failure(OrderErrors.BUYER_INVALID)
        // 1. 通过 ACL 查询商品信息
        val goodsIds = cmd.items.map { GoodsId(it.spuId, it.skuId) }
        val goodsInfoMap = goodsService.queryGoods(goodsIds).associateBy { it.id }
        val offerInfoMap =
            offerService.queryOffers(cmd.items.map { it.offerId }.distinct()).associateBy {
                it.offerId
            }

        val requestedMerchantId = MerchantId(cmd.merchantId)

        // 2. 构建 OrderItem
        val orderItems =
            cmd.items.map { itemCmd ->
                val goods =
                    goodsInfoMap[GoodsId(itemCmd.spuId, itemCmd.skuId)]
                        ?: return Failure(
                            OrderErrors.CORRESPONDING_GOODS_NOT_FOUND.msg(
                                "商品 SPU ID=${itemCmd.spuId} 快照不存在"
                            )
                        )

                if (goods.merchantId != requestedMerchantId.value) {
                    return Failure(OrderErrors.MERCHANT_MISMATCH)
                }

                // Catalog 快照版本校验（SPU 粒度）
                if (itemCmd.snapshotVersion != goods.snapshotVersion) {
                    return Failure(
                        OrderErrors.SNAPSHOT_VERSION_MISMATCH.msg(
                            "商品 SPU ID=${itemCmd.spuId} 信息已变更，请刷新页面"
                        )
                    )
                }

                val offer =
                    offerInfoMap[itemCmd.offerId]
                        ?: return Failure(
                            OrderErrors.CORRESPONDING_GOODS_NOT_FOUND.msg(
                                "销售要约 ID=${itemCmd.offerId} 不存在"
                            )
                        )
                if (
                    offer.skuId != itemCmd.skuId ||
                        offer.merchantId != requestedMerchantId.value ||
                        offer.version != itemCmd.offerVersion
                ) {
                    return Failure(OrderErrors.SNAPSHOT_VERSION_MISMATCH.msg("销售要约已变化，请刷新页面"))
                }

                OrderItemImpl(
                    id = OrderItemId(snowFlakSequence.nextId()),
                    spuId = itemCmd.spuId,
                    skuId = itemCmd.skuId,
                    offerId = offer.offerId,
                    storeId = offer.storeId,
                    offerVersion = offer.version,
                    fulfillmentNodeId = offer.fulfillmentNodeId,
                    channelId = offer.channelId,
                    goodsName = goods.spuName,
                    skuDescription = buildSkuDescription(goods.skuName, goods.attributes),
                    quantity = itemCmd.quantity,
                    unitPrice = offer.price,
                    snapshotVersion = goods.snapshotVersion,
                )
            }

        // 3. 计算总金额
        val itemsSubtotal = Price.sumOf(orderItems.map { it.subtotal() })
        val amountSnapshot = OrderAmountSnapshot.cny(itemsSubtotal)

        // 4. 从 RecipientInfoCMD 构建 ShippingInfo
        val recipientInfoCmd = cmd.recipientInfo
        val address =
            geoAddressService
                .getByCode(recipientInfoCmd.countryCode, recipientInfoCmd.shippingDistrictCode)
                .fold(
                    onSuccess = { it },
                    onFailure = {
                        return Failure(it)
                    },
                )

        val contractInfo =
            ContractInfo(
                email = recipientInfoCmd.consigneeContractInfo.emailAddress,
                phoneNumber = recipientInfoCmd.consigneeContractInfo.phoneNumber,
            )

        val recipientInfo =
            RecipientInfo(
                name = recipientInfoCmd.consigneeName,
                contractInfo = contractInfo,
                shippingAddress = address,
                shippingDetailAddress = recipientInfoCmd.shippingDetailAddress,
                postalCode = recipientInfoCmd.postalCode,
                customsFields = recipientInfoCmd.customsFields,
            )

        // 5. 组装聚合根
        val order =
            OrderImpl(
                id = OrderId(snowFlakSequence.nextId()),
                merchantId = requestedMerchantId,
                buyerInfo = buyerInfo,
                _items = orderItems.toMutableList(),
                recipientInfo = recipientInfo,
                _tradeStatus = TradeStatus.ACTIVE,
                _commitmentStatus = CommitmentStatus.CONFIRMED,
                _paymentStatus = PaymentStatus.UNPAID,
                _fulfillmentStatus = FulfillmentStatus.UNFULFILLED,
                amountSnapshot = amountSnapshot,
            )

        order.recordCreated()
        return Success(order)
    }

    private fun buildSkuDescription(
        skuName: String,
        attributes: List<Pair<String, String>>,
    ): String {
        if (attributes.isEmpty()) return skuName
        return attributes.joinToString(" ") { "${it.first}:${it.second}" }
    }
}
