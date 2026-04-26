package com.jstore.common.geo

import com.jstore.common.errors.BusinessError

object AddressErrors {
    val IllegalAddressCode: BusinessError = BusinessError("Illegal address code", "Address.Code.Illegal", 400)
}
