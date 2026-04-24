package com.jstore.common.properties

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 价格值对象（以"分"为单位存储）
 *
 * - 不可变、值语义（data class 自动生成 equals/hashCode/copy）
 * - 内部用 Long 存储分值，避免 BigDecimal 的精度陷阱
 * - 提供 operator 重载，支持 `+` `-` `*` 等自然写法
 * - 实现 Comparable，可直接比较大小
 */
@ConsistentCopyVisibility
data class Price private constructor(val fen: Long) : Comparable<Price> {

    init {
        require(fen >= 0) { "价格不能为负数: $fen" }
    }

    // ── 算术运算 ──

    operator fun plus(other: Price): Price = Price(fen + other.fen)

    operator fun minus(other: Price): Price = Price(fen - other.fen)

    operator fun times(quantity: Int): Price = Price(fen * quantity)

    operator fun times(quantity: Long): Price = Price(fen * quantity)

    operator fun times(factor: BigDecimal): Price =
        Price(BigDecimal(fen).multiply(factor).setScale(0, RoundingMode.HALF_UP).toLong())

    operator fun div(divisor: Int): Price =
        Price(BigDecimal(fen).divide(BigDecimal(divisor), 0, RoundingMode.HALF_UP).toLong())

    // ── 比较 ──

    override fun compareTo(other: Price): Int = fen.compareTo(other.fen)

    // ── 转换 ──

    /** 转为元（BigDecimal），保留 2 位小数 */
    fun toYuan(): BigDecimal = BigDecimal(fen).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)

    /** 转为 BigDecimal（分值） */
    fun toBigDecimal(): BigDecimal = BigDecimal(fen)

    override fun toString(): String = fen.toString()

    companion object {
        val ZERO: Price = Price(0)

        /** 以分为单位创建 */
        fun ofFen(fen: Long): Price = Price(fen)

        /** 以分为单位创建 */
        fun ofFen(fen: Int): Price = Price(fen.toLong())

        /** 以元为单位创建（自动转分，四舍五入） */
        fun ofYuan(yuan: BigDecimal): Price =
            Price(yuan.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong())

        /** 从数据库 BigDecimal（分值）还原 */
        fun fromBigDecimal(value: BigDecimal): Price = Price(value.toLong())

        /** 求和 */
        fun sumOf(prices: List<Price>): Price = Price(prices.sumOf { it.fen })
    }
}
