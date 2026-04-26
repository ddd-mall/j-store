package com.jstore.common.geo

import cn.idev.excel.FastExcel
import cn.idev.excel.annotation.ExcelProperty
import cn.idev.excel.context.AnalysisContext
import cn.idev.excel.read.listener.ReadListener
import com.jstore.common.errors.BusinessError
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.string.StringUtils
import java.util.concurrent.ConcurrentHashMap

open class ChinaGeoAddressServiceExcelImpl : GeoAddressService {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ChinaGeoAddressServiceExcelImpl::class)

        private val dataStorage: MutableMap<String, String> by lazy {
            val storage = ConcurrentHashMap<String, String>()
            val excelPath = "data/district.xlsx"
            val resource = ChinaGeoAddressServiceExcelImpl::class.java.classLoader.getResourceAsStream(excelPath)
            resource.use { fis ->
                FastExcel.read(fis, DistrictData::class.java, DistrictDataListener(storage))
                    .sheet()
                    .doRead()
            }
            log.info("[地址服务-excel版] - 已将地址数据从excel中加载到内存")
            storage
        }

        open class DistrictData {
            @ExcelProperty("district_name")
            var districtName: String? = null

            @ExcelProperty("district_code")
            var districtCode: String? = null
        }

        private class DistrictDataListener(val dataStorage: MutableMap<String, String>) : ReadListener<DistrictData> {
            override fun invoke(districtData: DistrictData?, analysisContext: AnalysisContext?) {
                districtData?.let { data ->
                    data.districtCode?.let { code ->
                        data.districtName?.let { name ->
                            dataStorage.putIfAbsent(code, name)
                        }
                    }
                }
            }

            override fun doAfterAllAnalysed(analysisContext: AnalysisContext?) {
                // loading complete
            }
        }
    }

    override fun getByDistrictCode(districtCode: String): Result<GeoAddressInfo, BusinessError> {
        if (districtCode.length < 6) {
            return Failure(AddressErrors.IllegalAddressCode.msg("地区编码${districtCode}格式错误，长度不能小于6位"))
        }
        dataStorage[districtCode]
            ?: return Failure(AddressErrors.IllegalAddressCode.msg("未能找到编码${districtCode}对应的地址"))

        val provinceCode = GeoAddressInfo.getProvinceCode(districtCode)
        val cityCode = GeoAddressInfo.getCityCode(districtCode)
        val countyCode = GeoAddressInfo.getCountyCode(districtCode)

        val province = dataStorage[provinceCode]
            ?: return Failure(AddressErrors.IllegalAddressCode.msg("未能找到编码${provinceCode}对应的地址"))
        val city = dataStorage[cityCode]?.let {
            if (it == province) "" else it
        } ?: "市辖区"
        val county = dataStorage[countyCode]?.let {
            if (it == province || it == city) "" else it
        } ?: ""

        if (StringUtils.isAllEmpty(province, city, county)) {
            return Failure(AddressErrors.IllegalAddressCode.msg("地区编码错误: $districtCode"))
        }
        return Success(GeoAddressInfo(districtCode, province, city, county))
    }
}
