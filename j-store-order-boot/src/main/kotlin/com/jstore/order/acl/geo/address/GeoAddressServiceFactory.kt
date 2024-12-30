package com.jstore.com.jstore.order.acl.geo.address

import com.jstore.common.utils.AbstractFactory
import com.jstore.order.acl.geo.address.GeoAddressService

class GeoAddressServiceFactory : AbstractFactory<GeoAddressService>(
    listOf(
        MockGeoAddressServiceImpl::class.java,
        ExcelGeoAddressServiceImpl::class.java
    )
) {
    fun newInstance(): GeoAddressService {
        return super.newInstance()
    }
}