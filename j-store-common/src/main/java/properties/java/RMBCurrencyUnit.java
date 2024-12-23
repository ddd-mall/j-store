package properties.java;

import java.math.BigDecimal;

public enum RMBCurrencyUnit implements CurrencyUnit<RMBCurrency> {
    YUAN {
        @Override
        public Money<RMBCurrency, CurrencyUnit<RMBCurrency>> ofValue(BigDecimal value) {
            return null;
        }

        @Override
        public BigDecimal rateOf(CurrencyUnit<? extends Currency> otherCurrencyUnit) {
            return null;
        }
    },
    JIAO {
        @Override
        public Money<RMBCurrency, CurrencyUnit<RMBCurrency>> ofValue(BigDecimal value) {
            return null;
        }

        @Override
        public BigDecimal rateOf(CurrencyUnit<? extends Currency> otherCurrencyUnit) {
            return null;
        }
    },
    FEN {
        @Override
        public Money<RMBCurrency, CurrencyUnit<RMBCurrency>> ofValue(BigDecimal value) {
            return null;
        }

        @Override
        public BigDecimal rateOf(CurrencyUnit<? extends Currency> otherCurrencyUnit) {
            return null;
        }
    }
    ;


}
