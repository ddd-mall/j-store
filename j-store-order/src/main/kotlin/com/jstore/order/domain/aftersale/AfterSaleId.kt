package com.jstore.order.domain.aftersale

import com.jstore.common.properties.Id

data class AfterSaleId(override val value: Long) : Id<Long>(value)
data class AfterSaleItemId(override val value: Long) : Id<Long>(value)
data class ApplicantActorId(override val value: Long) : Id<Long>(value)
data class MerchantActorId(override val value: Long) : Id<Long>(value)
