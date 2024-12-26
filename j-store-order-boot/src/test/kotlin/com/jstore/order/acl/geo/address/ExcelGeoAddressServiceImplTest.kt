package com.jstore.order.acl.geo.address

import com.jstore.com.jstore.order.acl.geo.address.GeoAddressServiceProxy
import com.jstore.com.jstore.order.saleorder.properties.DistrictLevel
import com.jstore.common.logging.LoggerFactory
import org.junit.jupiter.api.Test
import kotlin.test.asserter

class ExcelGeoAddressServiceImplTest {
    private val log = LoggerFactory.getLogger(ExcelGeoAddressServiceImplTest::class)
    @Test
    fun excelTest() {
        val geoAddressService = GeoAddressServiceProxy
        var byDistrictCode = geoAddressService.getByDistrictCode("460323").apply { detailAddress="某某街道某某路xxx号" }
        asserter.assertNotNull("can not find district from district code 110106", byDistrictCode)
        log.info("[地址信息]${byDistrictCode}")

        byDistrictCode = geoAddressService.getByDistrictCode("110106").apply { detailAddress="某某街道某某路xxx号" }
        asserter.assertNotNull("can not find district from district code 110106", byDistrictCode)
        log.info("[地址信息]${byDistrictCode}")

        byDistrictCode = geoAddressService.getByDistrictCode("110000").apply { detailAddress="某某街道某某路xxx号" }
        log.info("[地址信息]${byDistrictCode}")
        asserter.assertNotNull("can not find district from district code 110000", byDistrictCode)
        asserter.assertEquals("地址等级不符合预期", DistrictLevel.PROVINCE,  byDistrictCode.level)

        byDistrictCode = geoAddressService.getByDistrictCode("500101").apply { detailAddress="某某街道某某路xxx号" }
        log.info("[地址信息]${byDistrictCode}")
        asserter.assertNotNull("can not find district from district code 500101", byDistrictCode)
        asserter.assertEquals("地址等级不符合预期", DistrictLevel.COUNTY,  byDistrictCode.level)
    }
}