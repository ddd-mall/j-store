package com.jstore.common.properties

import java.math.BigDecimal


open class Price : RMB {
    constructor(value: BigDecimal): super(RMBCurrencyUnit.FEN, value)
    constructor(money: RMB) : this(money.to(RMBCurrencyUnit.FEN).getValue())
    constructor(unit: RMBCurrencyUnit, value: BigDecimal): super(unit, value)

    companion object {

        object Commonly {
            fun sumOf(priceList: List<Price>): Price {
                return Price(Money.sumOf(priceList).getBasicValue())
            }

            fun of(value: Int): Price {
                return Price(BigDecimal(value))
            }

            fun of(value: Long): Price {
                return Price(BigDecimal(value))
            }
        }
    }


    fun add(other: Price): Price {
        return Price(this.getValue().add(other.getValue(), getMathContext()))
    }

    fun sub(other: Price): Price {
        return Price(this.getValue().subtract(other.getValue(), getMathContext()))
    }

    fun multiple(other: Int): Price {
        return Price(this.getValue().multiply(BigDecimal(other, getMathContext())))
    }

    fun div(divisor: Price): Price {
        return Price(this.getValue().divide(divisor.getValue(), getMathContext()))
    }

    override fun toString(): String {
        return super.getValue().toString()
    }
}