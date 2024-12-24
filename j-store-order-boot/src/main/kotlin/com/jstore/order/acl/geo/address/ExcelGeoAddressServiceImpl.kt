package com.jstore.com.jstore.order.acl.geo.address

import cn.idev.excel.FastExcel
import cn.idev.excel.annotation.ExcelProperty
import cn.idev.excel.context.AnalysisContext
import cn.idev.excel.read.listener.ReadListener
import cn.idev.excel.support.ExcelTypeEnum
import com.jstore.com.jstore.order.saleorder.properties.GeoAddressInfo
import com.jstore.common.errors.CommonErrors
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.util.ResourceUtils
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap


@Service
open class ExcelGeoAddressServiceImpl : GeoAddressService {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ExcelGeoAddressServiceImpl::class.java)
        private val dataStorage: MutableMap<String, String> = ConcurrentHashMap<String, String>()

        init {
            val excelPath = "data/district.xlsx"
            val file = ResourceUtils.getFile("classpath:${excelPath}")
            val fileInputStream = FileInputStream(file)

            FastExcel.read(fileInputStream, DistrictData::class.java, DistrictDataListener(dataStorage))
                .excelType(ExcelTypeEnum.XLSX)
                .sheet()
                .doRead()
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
                log.info("[地址服务-excel版] - 已将地址数据从excel数据加载完毕")
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
    val geoAddressService = ExcelGeoAddressServiceImpl()
    val geoInfo = geoAddressService.getByDistrictCode("450324")
    println(geoInfo)
}