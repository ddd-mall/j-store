package com.jstore.common.properties

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

interface Money<C : Currency, U : CurrencyUnit<C>> {
    companion object {
        fun <C : Currency, U: CurrencyUnit<C>> sumOf(moneys: Collection<Money<C, U>>): Money<C, U> {
            return moneys.reduce { acc, money -> acc.sum(money) }
        }
    }
    fun of(currencyUnit: U, value: BigDecimal): Money<C, U>
    fun getCurrency(): Currency
    fun getCurrencyUnit(): U
    fun getValue(): BigDecimal
    fun getBasicValue(): BigDecimal

    fun sum(other: Money<C, U>): Money<C, U>
    fun sub(other: Money<C, U>): Money<C, U>

    fun multiply(value: BigDecimal): Money<C, U>
    fun multiply(value: Int): Money<C, U>
    fun multiply(value: Long): Money<C, U>


    fun divide(value: BigDecimal): Money<C, U>
    fun divide(value: Int): Money<C, U>
    fun divide(value: Long): Money<C, U>
}

interface Currency {
    companion object {
        private val defaultMathContext = MathContext(0, RoundingMode.HALF_EVEN)
    }
    fun basicUnit(): CurrencyUnit<out Currency>
    fun mathContext(): MathContext {
        return defaultMathContext
    }
}

interface CurrencyUnit<C : Currency> {
    fun ofValue(value: BigDecimal): Money<C, CurrencyUnit<C>>
    fun ofValue(value: Long): Money<C, CurrencyUnit<C>> {
        return ofValue(BigDecimal(value))
    }
    fun ofValue(value: Int): Money<C, CurrencyUnit<C>> {
        return ofValue(BigDecimal(value))
    }
    fun rateOf(otherCurrencyUnit: CurrencyUnit<out Currency>): BigDecimal
}

abstract class AbstractMoney<C : Currency, U : CurrencyUnit<C>> : Money<C, U> {

    override fun getBasicValue(): BigDecimal {
        return this.getCurrencyUnit().rateOf(getCurrency().basicUnit())
            .multiply(this.getValue(), getCurrency().mathContext())
    }

    override fun multiply(value: BigDecimal): Money<C, U> {
        return of(this.getCurrencyUnit(), this.getValue().multiply(value, getCurrency().mathContext()))
    }

    override fun multiply(value: Int): Money<C, U> {
        return of(
            this.getCurrencyUnit(),
            this.getValue().multiply(BigDecimal(value, this.getCurrency().mathContext()), this.getCurrency().mathContext())
        )
    }

    override fun multiply(value: Long): Money<C, U> {
        return of(
            this.getCurrencyUnit(),
            this.getValue().multiply(BigDecimal(value, this.getCurrency().mathContext()), this.getCurrency().mathContext())
        )
    }

    override fun divide(value: BigDecimal): Money<C, U> {
        return of(this.getCurrencyUnit(), this.getValue().divide(value, this.getCurrency().mathContext()))
    }

    override fun divide(value: Int): Money<C, U> {
        return of(
            this.getCurrencyUnit(),
            this.getValue().divide(BigDecimal(value, this.getCurrency().mathContext()), this.getCurrency().mathContext())
        )
    }

    override fun divide(value: Long): Money<C, U> {
        return of(
            this.getCurrencyUnit(),
            this.getValue().divide(BigDecimal(value, this.getCurrency().mathContext()), this.getCurrency().mathContext())
        )
    }

    override fun sub(other: Money<C, U>): Money<C, U> {
        val otherValues = other.getCurrencyUnit().rateOf(this.getCurrencyUnit()).multiply(other.getValue(), this.getCurrency().mathContext())
        return of(this.getCurrencyUnit(), this.getValue().subtract(otherValues, this.getCurrency().mathContext()))
    }

    override fun sum(other: Money<C, U>): Money<C, U> {
        val otherValues = other.getCurrencyUnit().rateOf(this.getCurrencyUnit()).multiply(other.getValue(), getCurrency().mathContext())
        return of(getCurrencyUnit(), this.getValue().add(otherValues, getCurrency().mathContext()))
    }
}