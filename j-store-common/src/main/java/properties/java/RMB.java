package properties.java;

import java.math.BigDecimal;

public class RMB extends AbstractMoney<RMBCurrency, RMBCurrencyUnit> {
    private final CurrencyUnit<? extends Currency> currencyUnit;
    private final BigDecimal value;

    public RMB(CurrencyUnit<? extends Currency> currencyUnit, BigDecimal value) {
        this.currencyUnit = currencyUnit;
        this.value = value;
    }

    @Override
    public Money<RMBCurrency, RMBCurrencyUnit> of(RMBCurrencyUnit currencyUnit, BigDecimal value) {
        return new RMB(currencyUnit, value);
    }

    @Override
    public Currency getCurrency() {
        return RMBCurrency.INSTANCE;
    }

    @Override
    public RMBCurrencyUnit getCurrencyUnit() {
        return currencyUnit;
    }

    @Override
    public BigDecimal getValue() {
        return null;
    }
}
