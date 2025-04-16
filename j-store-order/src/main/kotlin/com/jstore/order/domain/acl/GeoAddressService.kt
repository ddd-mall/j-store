package com.jstore.order.domain.acl

import com.jstore.order.domain.order.GeoAddressInfo

interface GeoAddressService {
    fun getByDistrictCode(districtCode: String): GeoAddressInfo
}