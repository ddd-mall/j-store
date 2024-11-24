package com.jstore.order.saleorder.properties

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode


data class Price(val value: BigDecimal) {

    companion object {
        private object CONFIG {
            var MATH_CONTEXT: MathContext = MathContext.DECIMAL32
        }

        fun setMathContext(precision: Int, roundingMode: RoundingMode) {
            CONFIG.MATH_CONTEXT = MathContext(precision, roundingMode)
        }

        object Commonly {
            fun sumOf(priceList: List<Price>): Price {
                return Price(priceList.sumOf { it.value })
            }

            fun of(value: Int): Price {
                return Price(BigDecimal(value))
            }

            fun of(value: Long): Price {
                return Price(BigDecimal(value))
            }
        }
    }


    fun plus(other: Price): Price {
        return Price(this.value.plus(other.value))
    }

    fun sub(other: Price): Price {
        return Price(this.value.subtract(other.value, CONFIG.MATH_CONTEXT))
    }

    fun multiple(other: Price): Price {
        return Price(this.value.multiply(other.value, CONFIG.MATH_CONTEXT))
    }

    fun multiple(other: Int): Price {
        return Price(this.value.multiply(BigDecimal(other)))
    }

    fun div(divisor: Price): Price {
        return Price(this.value.divide(divisor.value, CONFIG.MATH_CONTEXT))
    }
}