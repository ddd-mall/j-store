package com.jstore.order.service.acl

import com.jstore.order.domain.saleorder.properties.GeoAddressInfo

interface GeoAddressService {
    fun getByDistrictCode(districtCode: String): GeoAddressInfo
}