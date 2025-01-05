package com.jstore.order.acl.address

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.order.acl.GeoAddressService
import com.jstore.order.saleorder.properties.GeoAddressInfo

class MockAddressService : GeoAddressService {
    private val log: Logger = LoggerFactory.getLogger(MockAddressService::class)
        override fun getByDistrictCode(districtCode: String): GeoAddressInfo {
            val geoAddressInfo = GeoAddressInfo(districtCode, "MOCK PROVINCE", "MOCK CITY", "MOCK COUNTY")
            log.info("[地址服务-mock] - 通过地区编码${districtCode}获取地址信息: $geoAddressInfo")
            return geoAddressInfo
        }

}