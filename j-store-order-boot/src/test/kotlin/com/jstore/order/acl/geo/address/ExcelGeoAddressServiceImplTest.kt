package com.jstore.order.acl.geo.address

import com.jstore.com.jstore.order.acl.geo.address.ExcelGeoAddressServiceImpl
import com.jstore.common.logging.LoggerFactory
import org.junit.jupiter.api.Test
import kotlin.test.asserter

class ExcelGeoAddressServiceImplTest {
    @Test
    fun excelTest() {
        val geoAddressService = ExcelGeoAddressServiceImpl()
        val byDistrictCode = geoAddressService.getByDistrictCode("110106").apply { detailAddress="某某街道某某路xxx号" }
        asserter.assertNotNull("can not find district from district code 110106", byDistrictCode)
        val log = LoggerFactory.getLogger(ExcelGeoAddressServiceImplTest::class)
        log.info("[地址信息]${byDistrictCode}")
    }
}