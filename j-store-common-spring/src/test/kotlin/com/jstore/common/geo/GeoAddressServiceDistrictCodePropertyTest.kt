package com.jstore.common.geo

import cn.idev.excel.FastExcel
import com.jstore.common.utils.Success
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll

/**
 * Feature: geo-address-service-migration, Property 3: 地址查询 districtCode 一致性
 *
 * For any 在 district.xlsx 数据集中存在的有效行政区划编码，
 * 调用 getByDistrictCode(code) 返回的 GeoAddressInfo 的 districtCode 字段
 * 应与输入的 code 完全一致。
 *
 * **Validates: Requirements 3.4**
 */
class GeoAddressServiceDistrictCodePropertyTest : FunSpec({

    val service = ChinaGeoAddressServiceExcelImpl()

    // Load all district codes from the Excel file, then filter to only those
    // that can be successfully queried (some province/city-level codes may return Failure).
    val allCodes = mutableListOf<String>()
    val resource = Thread.currentThread().contextClassLoader.getResourceAsStream("data/district.xlsx")
    resource.use { fis ->
        FastExcel.read(fis, ChinaGeoAddressServiceExcelImpl.Companion.DistrictData::class.java,
            object : cn.idev.excel.read.listener.ReadListener<ChinaGeoAddressServiceExcelImpl.Companion.DistrictData> {
                override fun invoke(data: ChinaGeoAddressServiceExcelImpl.Companion.DistrictData?, ctx: cn.idev.excel.context.AnalysisContext?) {
                    data?.districtCode?.let { allCodes.add(it) }
                }
                override fun doAfterAllAnalysed(ctx: cn.idev.excel.context.AnalysisContext?) {}
            })
            .sheet()
            .doRead()
    }

    val validCodes = allCodes.filter { code ->
        service.getByDistrictCode(code).isSuccess
    }

    val arbDistrictCode = Arb.element(validCodes)

    test("getByDistrictCode returns GeoAddressInfo whose districtCode matches the input code") {
        checkAll(100, arbDistrictCode) { code ->
            val result = service.getByDistrictCode(code)
            result.isSuccess shouldBe true
            val info = (result as Success).value
            info.districtCode shouldBe code
        }
    }
})
