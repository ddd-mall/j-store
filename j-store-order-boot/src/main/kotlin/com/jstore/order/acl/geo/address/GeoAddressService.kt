package com.jstore.com.jstore.order.acl.geo.address

import com.jstore.com.jstore.order.saleorder.properties.GeoAddressInfo
import org.springframework.stereotype.Service

@Service
object GeoAddressServiceIml: GeoAddressService {
    override fun getByDistrictCode(districtCode: String): GeoAddressInfo {
        TODO("Not yet implemented")
    }
}