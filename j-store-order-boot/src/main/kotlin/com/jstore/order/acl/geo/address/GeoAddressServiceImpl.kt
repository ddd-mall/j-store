package com.jstore.com.jstore.order.acl.geo.address

import cn.idev.excel.FastExcel
import cn.idev.excel.annotation.ExcelProperty
import cn.idev.excel.context.AnalysisContext
import cn.idev.excel.read.listener.ReadListener
import com.jstore.order.saleorder.properties.GeoAddressInfo
import com.jstore.common.errors.CommonErrors
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.string.StringUtils
import com.jstore.order.acl.GeoAddressService
import org.springframework.stereotype.Service
import org.springframework.util.ResourceUtils
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

@Service
object GeoAddressServiceProxy : GeoAddressService {
    private val geoAddressServiceFactory = GeoAddressServiceFactory()
    private val geoAddressService = geoAddressServiceFactory.newInstance()


    override fun getByDistrictCode(districtCode: String): GeoAddressInfo {
        return geoAddressService.getByDistrictCode(districtCode)
    }
}





open class ChinaGeoAddressServiceExcelImpl : GeoAddressService {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ChinaGeoAddressServiceExcelImpl::class)
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
                log.info("[地址服务-excel版] - 已将地址数据从excel中加载到内存")
            }
        }
    }

    override fun getByDistrictCode(districtCode: String): GeoAddressInfo {
        if (districtCode.length < 6) {
            throw CommonErrors.INVALID_PARAM.to("地区编码错误: $districtCode")
        }
        val address = dataStorage[districtCode] ?: throw CommonErrors.RESOURCE_NOT_FOUND.to("未能找到编码${districtCode}对应的地址")
        val provinceCode = GeoAddressInfo.getProvinceCode(districtCode)
        val cityCode = GeoAddressInfo.getCityCode(districtCode)
        val countyCode = GeoAddressInfo.getCountyCode(districtCode)

        val province = dataStorage[provinceCode] ?: throw CommonErrors.RESOURCE_NOT_FOUND.to("未能找到编码${provinceCode}对应的地址")
        val city = dataStorage[cityCode]?.let {
            if (it == province) {
                ""
            } else {
                it
            }
        } ?: "市辖区"
        val county = dataStorage[countyCode]?.let {
            if (it == province || it == city) {
                ""
            } else {
                it
            }
        } ?: ""



        if (StringUtils.isAllEmpty(province, city, county)) {
            throw CommonErrors.INVALID_PARAM.to("地区编码错误: $districtCode")
        }
        return GeoAddressInfo(
            districtCode,
            province,
            city,
            county
        )
    }
}