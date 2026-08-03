package com.jstore.order.acl
import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result
import com.jstore.order.domain.aftersale.MerchantActorId
import com.jstore.order.domain.order.Order
interface AfterSaleMerchantResolver { fun merchantFor(order: Order): Result<MerchantActorId, BusinessError> }
