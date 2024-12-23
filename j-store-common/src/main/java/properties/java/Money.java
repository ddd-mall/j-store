package properties.java;

import java.math.BigDecimal;

public interface Money<C extends Currency, U extends CurrencyUnit<? extends Currency>> {
    Money<C, U> of(U currencyUnit, BigDecimal value);
    Currency getCurrency(); 
    U getCurrencyUnit();
    BigDecimal getValue();
    BigDecimal getBasicValue();
//    Money<C, U> sum(Money<C, U> other);
    Money<C, U> sub(Money<C, U> other);
    Money<C, U> multiply(BigDecimal value);
    Money<C, U> multiply(Integer value);
    Money<C, U> multiply(Long value);
    Money<C, U> divide(BigDecimal value);
    Money<C, U> divide(Integer value);
    Money<C, U> divide(Long value);

    Money<? extends Currency,? extends CurrencyUnit<? extends Currency>> sum(Money<? extends Currency, ? extends CurrencyUnit<? extends Currency>> money);
}
