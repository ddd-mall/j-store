package com.jstore.com.jstore.order.acl.geo.address

import cn.idev.excel.FastExcel
import cn.idev.excel.annotation.ExcelProperty
import cn.idev.excel.context.AnalysisContext
import cn.idev.excel.read.listener.ReadListener
import com.jstore.com.jstore.order.saleorder.properties.GeoAddressInfo
import com.jstore.common.errors.CommonErrors
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.util.ResourceUtils
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

@Service
object GeoAddressServiceProxy : GeoAddressService {
    private val log: Logger = LoggerFactory.getLogger(GeoAddressServiceProxy::class)
    private val geoAddressServiceFactory = GeoAddressServiceFactory()
    private val geoAddressService = geoAddressServiceFactory.newInstance(MockGeoAddressServiceImpl::class,"test", "test")
    override fun getByDistrictCode(districtCode: String): GeoAddressInfo {
        return geoAddressService.getByDistrictCode(districtCode)
    }
}



open class MockGeoAddressServiceImpl(private val name: String, private val value: String) : GeoAddressService {
    private val log: Logger = LoggerFactory.getLogger(MockGeoAddressServiceImpl::class)
    override fun getByDistrictCode(districtCode: String): GeoAddressInfo {
        val geoAddressInfo = GeoAddressInfo(districtCode, "MOCK PROVINCE", "MOCK CITY", "MOCK COUNTY")
        log.info("[地址服务-mock版:${name}] - 通过地区编码${districtCode}获取地址信息: $geoAddressInfo")
        return geoAddressInfo
    }
}

open class ExcelGeoAddressServiceImpl : GeoAddressService {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ExcelGeoAddressServiceImpl::class)
        private val dataStorage: MutableMap<String, String> = ConcurrentHashMap<String, String>()

        init {
            val excelPath = "data/district.xlsx"
            val file = ResourceUtils.getFile("classpath:${excelPath}")
            val fileInputStream = FileInputStream(file)
            fileInputStream.use { fis ->
                FastExcel.read(fis, DistrictData::class.java, DistrictDataListener(dataStorage))
                    .sheet()
                    .doRead()
            }
        }

        open class DistrictData {
            @ExcelProperty("district_name")
            var districtName: String? = null

            @ExcelProperty("district_code")
            var districtCode: String? = null
        }

        private class DistrictDataListener(val dataStorage: MutableMap<String, String>) : ReadListener<DistrictData> {
            override fun invoke(districtDAta: DistrictData?, analysisContext: AnalysisContext?) {
                districtDAta?.let { data ->
                    data.districtCode?.let { code ->
                        data.districtName?.let { name ->
                            dataStorage.putIfAbsent(code, name)
                        }
                    }
                }
            }

            override fun doAfterAllAnalysed(analysisContext: AnalysisContext?) {
                log.info("[地址服务-excel版] - 已将地址数据从excel加载到内存")
            }
        }
    }

    override fun getByDistrictCode(districtCode: String): GeoAddressInfo {
        if (districtCode.length < 6) {
            throw CommonErrors.INVALID_PARAM.withMsg("地区编码错误: $districtCode")
        }
        val provinceCode = GeoAddressInfo.getProvinceCode(districtCode)
        val cityCode = GeoAddressInfo.getCityCode(districtCode)
        val countyCode = GeoAddressInfo.getCountyCode(districtCode)
        return GeoAddressInfo(
            districtCode,
            dataStorage[provinceCode] ?: "",
            dataStorage[cityCode] ?: "",
            dataStorage[countyCode] ?: ""
        )
    }
}

fun main() {
    val log = LoggerFactory.getLogger(ExcelGeoAddressServiceImpl::class)

    val geoInfo = GeoAddressServiceProxy.getByDistrictCode("450324")
    log.info("{}", geoInfo)
}