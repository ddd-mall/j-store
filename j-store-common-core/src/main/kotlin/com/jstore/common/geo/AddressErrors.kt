package com.jstore.common.geo

import com.jstore.common.errors.BusinessError

object AddressErrors {
    val InvalidCode: BusinessError = BusinessError("Invalid address code", "Address.Code.Invalid", 400)
    val UnsupportedCountry: BusinessError = BusinessError("Unsupported country", "Address.Country.Unsupported", 400)
    val ComponentsEmpty: BusinessError = BusinessError("Address components empty", "Address.Components.Empty", 400)
}
