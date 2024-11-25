package com.jstore.com.jstore.order.saleorder.persistence

import com.jstore.common.properties.PhoneNumber

data class SaleOrderItemPO(
    private val id: Long,
    private val uid: Long,
    private val phoneNumber: String,
    private val userName: String,
) {


}