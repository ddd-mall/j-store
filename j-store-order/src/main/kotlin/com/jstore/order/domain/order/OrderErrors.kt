package com.jstore.order.domain.order

import com.jstore.common.errors.BusinessError

object OrderErrors {
    val ORDER_NOT_FOUND = BusinessError("订单不存在", "Order.NotFound", 404)
    val CORRESPONDING_GOODS_NOT_FOUND = BusinessError("对应商品资源不存在", "Order.Resource.NotFound", 404)
    val ILLEGAL_STATE = BusinessError("订单状态不合法", "Order.State.Invalid", 400)
    val ITEMS_EMPTY = BusinessError("订单商品不能为空", "Order.Items.Empty", 400)
    val BUYER_INVALID = BusinessError("买家信息无效", "Order.Buyer.Invalid", 400)
    val CONTRACT_INFO_INVALID = BusinessError("联系方式无效，邮箱和手机号至少提供一个", "Order.ContractInfo.Invalid", 400)
    val REFUND_ITEMS_EMPTY = BusinessError("退款行项列表不能为空", "Order.Refund.ItemsEmpty", 400)
    val REFUND_ITEM_NOT_FOUND = BusinessError("退款行项不存在", "Order.Refund.ItemNotFound", 400)
    val REFUND_ITEM_INVALID_STATE = BusinessError("退款行项状态不合法", "Order.Refund.ItemInvalidState", 400)
    val PAY_AMOUNT_INVALID = BusinessError("支付金额无效", "Order.Pay.AmountInvalid", 400)
    val CANCEL_REASON_INVALID = BusinessError("取消原因无效", "Order.Cancel.ReasonInvalid", 400)
    val REFUND_REASON_INVALID = BusinessError("退款原因无效", "Order.Refund.ReasonInvalid", 400)
    val REJECT_REASON_INVALID = BusinessError("拒绝退款原因无效", "Order.Refund.RejectReasonInvalid", 400)
    val CONSIGNEE_NAME_BLANK = BusinessError("收货人姓名不能为空", "Order.Consignee.NameBlank", 400)
    val DISTRICT_CODE_BLANK = BusinessError("行政区划编码不能为空", "Order.Consignee.DistrictCodeBlank", 400)
}