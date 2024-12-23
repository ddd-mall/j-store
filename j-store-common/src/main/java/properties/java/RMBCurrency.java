package properties.java;

public enum RMBCurrency implements Currency {
    INSTANCE;

    @Override
    public CurrencyUnit<? extends Currency> basicUnit() {
        return RMBCurrencyUnit.FEN;
    }
}
