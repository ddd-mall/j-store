package com.jstore.common.geo

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result
import org.springframework.stereotype.Service

@Service
class GeoAddressServiceProxy(
    private val delegate: GeoAddressService = ChinaGeoAddressServiceExcelImpl()
) : GeoAddressService {

    override fun getByDistrictCode(districtCode: String): Result<GeoAddressInfo, BusinessError> {
        return delegate.getByDistrictCode(districtCode)
    }
}
