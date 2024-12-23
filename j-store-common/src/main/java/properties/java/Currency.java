package properties.java;

import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collection;

public interface Currency {
    static Money<? extends Currency, ? extends CurrencyUnit<? extends Currency>> sumOf(Collection<Money<? extends Currency, ? extends CurrencyUnit<? extends Currency>>> moneys) {
        return moneys.stream().reduce(Money::sum).orElse(null);
    }
    MathContext defaultMathContext = new MathContext(0, RoundingMode.HALF_EVEN);
    CurrencyUnit<? extends Currency> basicUnit();
    default MathContext mathContext()  {
        return defaultMathContext;
    }
}
