package com.jstore.order.acl

import com.jstore.order.domain.saleorder.properties.GeoAddressInfo

interface GeoAddressService {
    fun getByDistrictCode(districtCode: String): GeoAddressInfo
}