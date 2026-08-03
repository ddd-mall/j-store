package com.jstore.order.acl
import com.jstore.common.utils.Success
import com.jstore.order.domain.aftersale.MerchantActorId
import com.jstore.order.domain.order.Order
class ConfiguredAfterSaleMerchantResolver(private val merchantId:Long):AfterSaleMerchantResolver{init{require(merchantId>0)};override fun merchantFor(order:Order)=Success(MerchantActorId(merchantId))}
