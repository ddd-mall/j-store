package com.jstore.common.geo

/**
 * 通用行政区划层级，Level 0 = 国家级，Level 1 = 最高行政区划，依次递增
 */
data class DivisionLevel(val depth: Int, val name: String) {
    init {
        require(depth >= 0) { "Division level depth must be non-negative" }
    }
}

/**
 * 国家行政区划层级配置
 */
data class DivisionLevelConfig(
    val countryCode: CountryCode,
    val levels: List<DivisionLevel>
) {
    init {
        require(levels.isNotEmpty()) { "Division levels must not be empty" }
    }

    val maxDepth: Int get() = levels.maxOf { it.depth }
}
