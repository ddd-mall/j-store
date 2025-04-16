package com.jstore.order.service.acl

import com.jstore.order.domain.order.GeoAddressInfo

interface GeoAddressService {
    fun getByDistrictCode(districtCode: String): GeoAddressInfo
}