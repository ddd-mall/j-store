package com.jstore.common.geo.chinese

/**
 * 中国行政区划编码工具
 * 从行政区划编码中按层级提取上级编码
 *
 * 中国行政区划编码结构：
 * - 省级：前2位有效，后补0（如 110000）
 * - 市级：前4位有效，后补0（如 110100）
 * - 区/县级：前6位有效，后补0（如 110105）
 * - 街道/乡镇级：前9位有效，后补0（如 110105001，预留扩展）
 */
object DistrictCodeUtils {

    /**
     * 各层级编码的有效前缀位数（按 depth 顺序）
     * depth 1 = 省(2位), depth 2 = 市(4位), depth 3 = 区/县(6位), depth 4 = 街道/乡镇(9位)
     */
    val LEVEL_PREFIX_LENGTHS: List<Int> = listOf(2, 4, 6, 9)

    /**
     * 泛化方法：按层级索引(0-based)提取编码
     * @param districtCode 完整行政区划编码
     * @param levelIndex 层级索引，0=省, 1=市, 2=区/县, 3=街道/乡镇
     * @return 该层级的标准编码（有效前缀 + 补0至原编码长度）
     */
    fun getCodeAtLevel(districtCode: String, levelIndex: Int): String {
        require(levelIndex in LEVEL_PREFIX_LENGTHS.indices) {
            "层级索引越界: $levelIndex, 合法范围 0..${LEVEL_PREFIX_LENGTHS.size - 1}"
        }
        val prefixLen = LEVEL_PREFIX_LENGTHS[levelIndex]
        require(districtCode.length >= prefixLen) {
            "行政区划编码长度不足，无法提取第${levelIndex}级编码: $districtCode (需要至少${prefixLen}位)"
        }
        return districtCode.substring(0, prefixLen) + "0".repeat(districtCode.length - prefixLen)
    }

    /** 提取省级编码：前2位 + 补0 */
    fun getProvinceCode(districtCode: String): String = getCodeAtLevel(districtCode, 0)

    /** 提取市级编码：前4位 + 补0 */
    fun getCityCode(districtCode: String): String = getCodeAtLevel(districtCode, 1)

    /** 提取区/县级编码：前6位 + 补0 */
    fun getCountyCode(districtCode: String): String = getCodeAtLevel(districtCode, 2)

    /** 提取街道/乡镇级编码：前9位 + 补0 */
    fun getTownCode(districtCode: String): String = getCodeAtLevel(districtCode, 3)
}
