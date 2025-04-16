package com.jstore.order.acl

import com.jstore.order.domain.order.GeoAddressInfo

interface GeoAddressService {
    fun getByDistrictCode(districtCode: String): GeoAddressInfo
}