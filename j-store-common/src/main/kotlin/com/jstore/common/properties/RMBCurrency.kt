package com.jstore.common.properties

import java.math.BigDecimal


enum class RMBCurrency: Currency {
    INSTANCE;
    override fun basicUnit(): CurrencyUnit<out Currency> {
        return RMBCurrencyUnit.FEN
    }
}

class RMB(private val currencyUnit: CurrencyUnit<RMBCurrency>, private val value: BigDecimal): AbstractMoney<RMBCurrency, CurrencyUnit<RMBCurrency>>() {

    override fun of(currencyUnit: CurrencyUnit<RMBCurrency>, value: BigDecimal): Money<in Currency, in CurrencyUnit<in Currency>> {
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


enum class RMBCurrencyUnit: CurrencyUnit<RMBCurrency> {
    YUAN {
        override fun ofValue(value: BigDecimal): Money<RMBCurrency, CurrencyUnit<RMBCurrency>> {
            return RMB(this, value)
        }

        override fun rateOf(otherCurrencyUnit: CurrencyUnit<out Currency>): BigDecimal {
            return when(otherCurrencyUnit) {
                FEN -> BigDecimal(100, RMBCurrency.INSTANCE.mathContext())
                JIAO -> BigDecimal(10, RMBCurrency.INSTANCE.mathContext())
                YUAN -> BigDecimal(1, RMBCurrency.INSTANCE.mathContext())
                else -> throw IllegalArgumentException("Unsupported currency unit")
            }
        }
    },
    JIAO {
        override fun ofValue(value: BigDecimal): Money<RMBCurrency, CurrencyUnit<RMBCurrency>> {
            return RMB(this, value)
        }

        override fun rateOf(otherCurrencyUnit: CurrencyUnit<out Currency>): BigDecimal {
            return when(otherCurrencyUnit) {
                FEN -> BigDecimal(10, RMBCurrency.INSTANCE.mathContext())
                JIAO -> BigDecimal(1, RMBCurrency.INSTANCE.mathContext())
                YUAN -> BigDecimal(0.1, RMBCurrency.INSTANCE.mathContext())
                else -> throw IllegalArgumentException("Unsupported currency unit")
            }
        }
    },
    FEN {
        override fun ofValue(value: BigDecimal): Money<RMBCurrency, CurrencyUnit<RMBCurrency>> {
            return RMB(this, value)
        }

        override fun rateOf(otherCurrencyUnit: CurrencyUnit<out Currency>): BigDecimal {
            return when(otherCurrencyUnit) {
                FEN -> BigDecimal(1, RMBCurrency.INSTANCE.mathContext())
                JIAO -> BigDecimal(0.1, RMBCurrency.INSTANCE.mathContext())
                YUAN -> BigDecimal(0.01, RMBCurrency.INSTANCE.mathContext())
                else -> throw IllegalArgumentException("Unsupported currency unit")
            }
        }
    }
}