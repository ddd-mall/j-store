package com.jstore.order.saleorder

import com.jstore.order.acl.GoodsId
import com.jstore.order.saleorder.properties.UserInfo

class SaleOrderCreateCMD {
    var buyerUserInfo: UserInfo? = null
    var purchaseItemList: List<PurchaseItem>? = null
    var districtCode: String = ""
    var detailAddress: String = ""

    class PurchaseItem {
        var spuId: Long? = null
        var skuId: Long? = null
        var count: Int? = 0

        fun mapToGoodsId(): GoodsId {
            return GoodsId(
                spuId ?: throw IllegalArgumentException("spuId can not be null"),
                skuId ?: throw IllegalArgumentException("skuId can not be null")
            )
        }

        override fun toString(): String {
            return "PurchaseItem(spuId=$spuId, skuId=$skuId, count=$count)"
        }
    }
}