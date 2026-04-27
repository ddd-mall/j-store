package com.jstore.order.domain.order

import com.jstore.common.errors.BusinessError
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.fold
import com.jstore.common.geo.GeoAddressService
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.GoodsService
import com.jstore.order.domain.order.command.OrderCreateCMD
import com.jstore.order.domain.order.event.OrderCreatedEvent
import com.jstore.order.domain.order.event.OrderItemSnapshot

/**
 * 订单工厂
 * 负责组装一个合法的初始状态的 Order 聚合根
 * 创建过程需要跨上下文查询（商品价格、地址信息），这些依赖不应注入到聚合根
 */
interface OrderFactory {
    fun create(cmd: OrderCreateCMD): Result<Order, BusinessError>
}

class OrderFactoryImpl(
    private val snowFlakSequence: SnowFlakSequence,
    private val goodsService: GoodsService,
    private val geoAddressService: GeoAddressService,
) : OrderFactory {

    override fun create(cmd: OrderCreateCMD): Result<Order, BusinessError> {
        // 1. 通过 ACL 查询商品信息
        val goodsIds = cmd.items.map { GoodsId(it.spuId, it.skuId) }
        val goodsInfoMap = goodsService.queryGoods(goodsIds).associateBy { it.id }

        // 2. 构建 OrderItem
        val orderItems = cmd.items.map { itemCmd ->
            val goods = goodsInfoMap[GoodsId(itemCmd.spuId, itemCmd.skuId)]
                ?: return Failure(OrderErrors.CORRESPONDING_GOODS_NOT_FOUND)
            OrderItemImpl(
                id = OrderItemId(snowFlakSequence.nextId()),
                spuId = itemCmd.spuId,
                skuId = itemCmd.skuId,
                goodsName = "",
                skuDescription = "",
                quantity = itemCmd.quantity,
                unitPrice = goods.price,
            )
        }

        // 3. 计算总金额
        val totalAmount = Price.sumOf(orderItems.map { it.subtotal() })

        // 4. 从 RecipientInfoCMD 构建 ShippingInfo
        val recipientInfoCmd = cmd.recipientInfo
        val countryCode = recipientInfoCmd.countryCode ?: "CN"
        val address = geoAddressService.getByCode(countryCode, recipientInfoCmd.shippingDistrictCode)
            .fold(
                onSuccess = { it },
                onFailure = { return Failure(it) }
            )

        val contractInfo = ContractInfo(
            email = recipientInfoCmd.consigneeContractInfo.emailAddress,
            phoneNumber = recipientInfoCmd.consigneeContractInfo.phoneNumber,
        )

        val recipientInfo = RecipientInfo(
            name = recipientInfoCmd.consigneeName,
            contractInfo = contractInfo,
            shippingAddress = address,
            shippingDetailAddress = recipientInfoCmd.shippingDetailAddress,
        )

        // 5. 组装聚合根
        val order = OrderImpl(
            id = OrderId(snowFlakSequence.nextId()),
            buyerInfo = UserInfo(
                uid = cmd.buyerUid,
                phoneNumber = cmd.buyerPhone?.let { PhoneNumber(it) },
                userName = cmd.buyerName,
            ),
            _items = orderItems.toMutableList(),
            recipientInfo = recipientInfo,
            _status = OrderStatus.PENDING_STOCK,
            totalAmount = totalAmount,
            _actualPay = totalAmount,
        )

        order.publishEvent(OrderCreatedEvent(
            orderId = order.id,
            totalAmount = totalAmount,
            items = orderItems.map { OrderItemSnapshot(skuId = it.skuId, quantity = it.quantity) }
        ))
        return Success(order)
    }
}
