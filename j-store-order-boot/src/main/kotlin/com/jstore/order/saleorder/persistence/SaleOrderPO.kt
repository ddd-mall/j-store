package com.jstore.com.jstore.order.saleorder.persistence

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity

open class SaleOrderPO {

    @Id

    @GeneratedValue(strategy = GenerationType.AUTO)

    var autoIncrementId: Long? = null
    var saleOrderId: Long? = null
    var uid: Long? = null
    var phoneNumber: String? = null
    var userName: String? = null
}
