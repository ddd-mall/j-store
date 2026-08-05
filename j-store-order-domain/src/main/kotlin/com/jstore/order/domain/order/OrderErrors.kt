package com.jstore.order.domain.order

import com.jstore.common.errors.BusinessError

object OrderErrors {
    val ORDER_NOT_FOUND = BusinessError("订单不存在", "Order.NotFound", 404)
    val CORRESPONDING_GOODS_NOT_FOUND = BusinessError("对应商品资源不存在", "Order.Resource.NotFound", 404)
    val ILLEGAL_STATE = BusinessError("订单状态不合法", "Order.State.Invalid", 400)
    val ITEMS_EMPTY = BusinessError("订单商品不能为空", "Order.Items.Empty", 400)
    val BUYER_INVALID = BusinessError("买家信息无效", "Order.Buyer.Invalid", 400)
    val CONTRACT_INFO_INVALID =
        BusinessError("联系方式无效，邮箱和手机号至少提供一个", "Order.ContractInfo.Invalid", 400)
    val REFUND_ITEMS_EMPTY = BusinessError("退款行项列表不能为空", "Order.Refund.ItemsEmpty", 400)
    val REFUND_ITEM_NOT_FOUND = BusinessError("退款行项不存在", "Order.Refund.ItemNotFound", 400)
    val REFUND_ITEM_INVALID_STATE = BusinessError("退款行项状态不合法", "Order.Refund.ItemInvalidState", 400)
    val MERCHANT_INVALID = BusinessError("订单商户无效", "Order.Merchant.Invalid", 400)
    val MERCHANT_MISMATCH = BusinessError("商品不属于同一商户", "Order.Merchant.Mismatch", 409)
    val PAYMENT_FACT_INVALID = BusinessError("支付事实无效", "Order.PaymentFact.Invalid", 409)
    val PAYMENT_REFERENCE_CONFLICT =
        BusinessError("订单已关联其他支付单", "Order.PaymentReference.Conflict", 409)
    val FULFILLMENT_FACT_INVALID = BusinessError("履约事实无效", "Order.FulfillmentFact.Invalid", 409)
    val CANCEL_REASON_INVALID = BusinessError("取消原因无效", "Order.Cancel.ReasonInvalid", 400)
    val REFUND_REASON_INVALID = BusinessError("退款原因无效", "Order.Refund.ReasonInvalid", 400)
    val REJECT_REASON_INVALID = BusinessError("拒绝退款原因无效", "Order.Refund.RejectReasonInvalid", 400)
    val REFUND_PROJECTION_INVALID = BusinessError("退款投影无效", "Order.RefundProjection.Invalid", 409)
    val CONSIGNEE_NAME_BLANK = BusinessError("收货人姓名不能为空", "Order.Consignee.NameBlank", 400)
    val DISTRICT_CODE_BLANK = BusinessError("行政区划编码不能为空", "Order.Consignee.DistrictCodeBlank", 400)
    val SNAPSHOT_VERSION_MISMATCH =
        BusinessError("商品信息已变更，请刷新页面后重新下单", "Order.Snapshot.VersionMismatch", 409)
}
