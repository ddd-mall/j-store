package com.jstore.com.jstore.order.acl.geo.address

import com.jstore.com.jstore.order.saleorder.properties.GeoAddressInfo
import org.springframework.stereotype.Service

@Service
open class GeoAddressServiceIml: GeoAddressService {

    override fun getByDistrictCode(districtCode: String): GeoAddressInfo {
        return GeoAddressInfo(
            districtCode,
            "Mock country",
            "Zhejiang",
            "Hangzhou",
            "MOCK address"
        )
    }
}