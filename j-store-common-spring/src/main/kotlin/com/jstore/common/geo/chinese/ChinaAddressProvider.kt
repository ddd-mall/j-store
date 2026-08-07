/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.common.geo.chinese

import cn.idev.excel.FastExcel
import cn.idev.excel.annotation.ExcelProperty
import cn.idev.excel.context.AnalysisContext
import cn.idev.excel.read.listener.ReadListener
import com.jstore.common.errors.BusinessError
import com.jstore.common.geo.AddressComponent
import com.jstore.common.geo.AddressErrors
import com.jstore.common.geo.AddressTemplate
import com.jstore.common.geo.CountryAddressProvider
import com.jstore.common.geo.CountryCode
import com.jstore.common.geo.DivisionLevel
import com.jstore.common.geo.DivisionLevelConfig
import com.jstore.common.geo.I18nGeoAddress
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.fold
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

/**
 * 中国地址提供者 直接从 Excel 加载行政区划数据，构建 I18nGeoAddress
 *
 * 层级元数据驱动：通过 DIVISION_LEVEL_META 定义各级行政区划， 新增层级（如街道/乡镇）只需扩展元数据列表和数据源，无需修改构建逻辑。
 */
@Component
class ChinaAddressProvider : CountryAddressProvider {

    /** 层级元数据：将 DivisionLevel 与编码前缀位数绑定 levelIndex 对应 DistrictCodeUtils.LEVEL_PREFIX_LENGTHS 的索引 */
    data class LevelMeta(
        val level: DivisionLevel,
        val levelIndex: Int,
    )

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ChinaAddressProvider::class)

        /** 中国行政区划层级元数据（数据驱动） 扩展到4级只需在此追加一行：LevelMeta(DivisionLevel(4, "街道/乡镇"), 3) */
        val DIVISION_LEVEL_META: List<LevelMeta> =
            listOf(
                LevelMeta(DivisionLevel(1, "省"), 0),
                LevelMeta(DivisionLevel(2, "市"), 1),
                LevelMeta(DivisionLevel(3, "区/县"), 2),
                // 预留第4级：取消注释即可启用
                // LevelMeta(DivisionLevel(4, "街道/乡镇"), 3),
            )

        /** 当前支持的合法编码长度集合，从层级元数据自动派生 */
        val VALID_CODE_LENGTHS: Set<Int> =
            DIVISION_LEVEL_META.map { DistrictCodeUtils.LEVEL_PREFIX_LENGTHS[it.levelIndex] }
                .toSet()

        private val dataStorage: Map<String, String> by lazy {
            val storage = ConcurrentHashMap<String, String>()
            val excelPath = "data/district.xlsx"
            val resource =
                ChinaAddressProvider::class.java.classLoader.getResourceAsStream(excelPath)
            resource.use { fis ->
                FastExcel.read(fis, DistrictData::class.java, DistrictDataListener(storage))
                    .sheet()
                    .doRead()
            }
            log.info("[地址服务-ChinaAddressProvider] - 已将地址数据从excel中加载到内存")
            storage
        }

        open class DistrictData {
            @ExcelProperty("district_name") var districtName: String? = null

            @ExcelProperty("district_code") var districtCode: String? = null
        }

        private class DistrictDataListener(val dataStorage: MutableMap<String, String>) :
            ReadListener<DistrictData> {
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

    private val template = ChinaAddressTemplate()

    override fun supportedCountryCode(): CountryCode = CountryCode.CN

    override fun validateCode(addressCode: String): Result<Unit, BusinessError> {
        if (!addressCode.all { it.isDigit() } || addressCode.length !in VALID_CODE_LENGTHS) {
            val lengths = VALID_CODE_LENGTHS.sorted().joinToString("/")
            return Failure(AddressErrors.InvalidCode.msg("中国地址编码必须为${lengths}位数字: $addressCode"))
        }
        return Success(Unit)
    }

    override fun getDivisionLevelConfig(): DivisionLevelConfig =
        DivisionLevelConfig(
            countryCode = CountryCode.CN,
            levels = DIVISION_LEVEL_META.map { it.level },
        )

    override fun getByCode(addressCode: String): Result<I18nGeoAddress, BusinessError> {
        return validateCode(addressCode)
            .fold(
                onSuccess = { buildI18nGeoAddress(addressCode) },
                onFailure = { Failure(it) },
            )
    }

    override fun getAddressTemplate(): AddressTemplate = template

    /** 数据驱动构建 I18nGeoAddress 遍历层级元数据，按编码前缀提取各级地址名称，自动去重（跳过与上级同名的层级） */
    private fun buildI18nGeoAddress(districtCode: String): Result<I18nGeoAddress, BusinessError> {
        dataStorage[districtCode]
            ?: return Failure(AddressErrors.InvalidCode.msg("未能找到编码${districtCode}对应的地址"))

        val zhCN = Locale.SIMPLIFIED_CHINESE
        val components = mutableListOf<AddressComponent>()
        val usedNames = mutableSetOf<String>()

        for (meta in DIVISION_LEVEL_META) {
            val prefixLen = DistrictCodeUtils.LEVEL_PREFIX_LENGTHS[meta.levelIndex]
            // 编码长度不足以覆盖该层级，跳过
            if (districtCode.length < prefixLen) break

            val levelCode = DistrictCodeUtils.getCodeAtLevel(districtCode, meta.levelIndex)
            val name = dataStorage[levelCode] ?: continue

            // 去重：跳过与上级行政区划同名的层级（如直辖市省市同名）
            if (name in usedNames) continue
            usedNames.add(name)

            components.add(
                AddressComponent(
                    code = levelCode,
                    level = meta.level,
                    names = mapOf(zhCN to name),
                    defaultLocale = zhCN,
                )
            )
        }

        if (components.isEmpty()) {
            return Failure(AddressErrors.InvalidCode.msg("地区编码错误: $districtCode"))
        }

        return Success(
            I18nGeoAddress(
                countryCode = CountryCode.CN,
                components = components,
            )
        )
    }
}
