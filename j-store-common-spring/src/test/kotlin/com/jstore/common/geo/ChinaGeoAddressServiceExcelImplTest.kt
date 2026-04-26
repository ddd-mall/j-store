package com.jstore.common.geo

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.common.utils.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.string.shouldContain

/**
 * Unit tests for ChinaGeoAddressServiceExcelImpl.
 *
 * Validates: Requirements 3.4
 */
class ChinaGeoAddressServiceExcelImplTest : FunSpec({

    val service = ChinaGeoAddressServiceExcelImpl()

    context("编码长度 < 6 时返回 Failure") {
        test("长度为5的编码应返回 Failure") {
            val result = service.getByDistrictCode("12345")
            result.isFailure shouldBe true
            (result as Failure).error.message shouldContain "长度不能小于6位"
        }

        test("空字符串应返回 Failure") {
            val result = service.getByDistrictCode("")
            result.isFailure shouldBe true
            (result as Failure).error.message shouldContain "长度不能小于6位"
        }
    }

    context("不存在的编码返回 Failure") {
        test("数据集中不存在的6位编码应返回 Failure") {
            val result = service.getByDistrictCode("999999")
            result.isFailure shouldBe true
            (result as Failure).error.message shouldContain "未能找到编码"
        }
    }

    context("已知有效编码返回正确的省/市/区信息") {
        test("省级编码 110000 返回北京市") {
            val result = service.getByDistrictCode("110000")
            result.isSuccess shouldBe true
            val info = (result as Success).value
            info.districtCode shouldBe "110000"
            info.province shouldBe "北京市"
        }

        test("区级编码 110105 返回北京市朝阳区") {
            val result = service.getByDistrictCode("110105")
            result.isSuccess shouldBe true
            val info = (result as Success).value
            info.districtCode shouldBe "110105"
            info.province shouldBe "北京市"
            info.county shouldBe "朝阳区"
        }

        test("非直辖市编码 330100 返回浙江省杭州市") {
            val result = service.getByDistrictCode("330100")
            result.isSuccess shouldBe true
            val info = (result as Success).value
            info.districtCode shouldBe "330100"
            info.province shouldBe "浙江省"
            info.city shouldBe "杭州市"
        }
    }
})
