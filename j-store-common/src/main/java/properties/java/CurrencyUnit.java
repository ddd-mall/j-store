package properties.java;

import java.math.BigDecimal;

public interface CurrencyUnit<C extends Currency> {
    Money<C, CurrencyUnit<C>> ofValue(BigDecimal value);
    default Money<C, CurrencyUnit<C>> ofValue(Long value)  {
        return ofValue(new BigDecimal(value));
    }
    default Money<C, CurrencyUnit<C>> ofValue(Integer value )  {
        return ofValue(new BigDecimal(value));
    }
    BigDecimal rateOf(CurrencyUnit<? extends Currency> otherCurrencyUnit);
}
