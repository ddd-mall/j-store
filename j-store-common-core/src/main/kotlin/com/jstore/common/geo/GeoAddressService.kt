package com.jstore.common.geo

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result

interface GeoAddressService {
    fun getByDistrictCode(districtCode: String): Result<GeoAddressInfo, BusinessError>
}
