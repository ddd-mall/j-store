package com.jstore.com.jstore.order.acl.geo.address

import com.jstore.common.utils.AbstractFactory

class GeoAddressServiceFactory : AbstractFactory<GeoAddressService>(
    listOf(
        MockGeoAddressServiceImpl::class.java,
        ExcelGeoAddressServiceImpl::class.java
    )
)



fun main() {
    val byDistrictCode = GeoAddressServiceProxy.getByDistrictCode("110106")
    println(byDistrictCode)
}