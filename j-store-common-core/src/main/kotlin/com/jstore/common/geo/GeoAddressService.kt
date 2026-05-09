package com.jstore.common.geo

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result

interface GeoAddressService {
    fun getByCode(countryCode: String, addressCode: String): Result<I18nGeoAddress, BusinessError>
}
