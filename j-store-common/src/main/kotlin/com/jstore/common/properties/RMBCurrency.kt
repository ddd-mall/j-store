package com.jstore.common.properties

import java.math.BigDecimal


enum class RMBCurrency : Currency {
    INSTANCE;
}

open class RMB(private val currencyUnit: CurrencyUnit<RMBCurrency>, private val value: BigDecimal) :
    AbstractMoney<RMBCurrency, CurrencyUnit<RMBCurrency>>() {

    override fun of(currencyUnit: CurrencyUnit<RMBCurrency>, value: BigDecimal): Money<RMBCurrency, CurrencyUnit<RMBCurrency>> {
        return RMB(currencyUnit, value)
    }

    override fun getCurrency(): Currency {
        return RMBCurrency.INSTANCE
    }

    override fun getCurrencyUnit(): CurrencyUnit<RMBCurrency> {
        return currencyUnit
    }

    override fun getValue(): BigDecimal {
        return value
    }
}


enum class RMBCurrencyUnit : CurrencyUnit<RMBCurrency> {
    YUAN {
        override fun rateOf(otherCurrencyUnit: CurrencyUnit<RMBCurrency>): BigDecimal {
            return when (otherCurrencyUnit) {
                FEN -> BigDecimal(100, RMBCurrency.INSTANCE.mathContext())
                JIAO -> BigDecimal(10, RMBCurrency.INSTANCE.mathContext())
                YUAN -> BigDecimal(1, RMBCurrency.INSTANCE.mathContext())
                else -> throw IllegalArgumentException("Unsupported currency unit")
            }
        }
    },
    JIAO {
        override fun rateOf(otherCurrencyUnit: CurrencyUnit<RMBCurrency>): BigDecimal {
            return when (otherCurrencyUnit) {
                FEN -> BigDecimal(10, getCurrency().mathContext())
                JIAO -> BigDecimal(1, getCurrency().mathContext())
                YUAN -> BigDecimal(0.1, getCurrency().mathContext())
                else -> throw IllegalArgumentException("Unsupported currency unit")
            }
        }
    },
    FEN {
        override fun rateOf(otherCurrencyUnit: CurrencyUnit<RMBCurrency>): BigDecimal {
            return when (otherCurrencyUnit) {
                FEN -> BigDecimal(1, getCurrency().mathContext())
                JIAO -> BigDecimal(0.1, getCurrency().mathContext())
                YUAN -> BigDecimal(0.01, getCurrency().mathContext())
                else -> throw IllegalArgumentException("Unsupported currency unit")
            }
        }
    };

    override fun basicUnit(): CurrencyUnit<RMBCurrency> {
        return FEN
    }

    override fun getCurrency(): RMBCurrency {
        return RMBCurrency.INSTANCE
    }

    override fun ofValue(value: BigDecimal): Money<RMBCurrency, CurrencyUnit<RMBCurrency>> {
        return RMB(this, value)
    }
}