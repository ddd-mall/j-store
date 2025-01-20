package com.jstore.com.jstore.order.acl.geo

import com.jstore.common.errors.Errors

object AddressErrors {
    val IllegalAddressCode: Errors = Errors("Illegal address code", "Address.Code.Illegal", 400)
}