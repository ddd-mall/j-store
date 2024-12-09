package com.jstore.com.jstore.order.acl.geo.address

import com.jstore.com.jstore.order.saleorder.properties.GeoAddressInfo

interface GeoAddressService {
    fun getByDistrictCode(districtCode: String): GeoAddressInfo
}