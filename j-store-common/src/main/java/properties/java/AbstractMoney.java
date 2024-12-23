package properties.java;


import java.math.BigDecimal;

public abstract class AbstractMoney<C extends Currency, U extends CurrencyUnit<? extends Currency>> implements Money<C, U> {

    @Override
    public BigDecimal getBasicValue()  {
        return this.getCurrencyUnit().rateOf(getCurrency().basicUnit())
                .multiply(this.getValue(), getCurrency().mathContext());
    }

    @Override
    public Money<C, U> multiply(BigDecimal value) {
        return of(this.getCurrencyUnit(), this.getValue().multiply(value, getCurrency().mathContext()));
    }

    @Override
    public Money<C, U> multiply(Integer value) {
        return of(
                this.getCurrencyUnit(),
                this.getValue().multiply(new BigDecimal(value, this.getCurrency().mathContext()), this.getCurrency().mathContext())
        );
    }

    @Override
    public Money<C, U> multiply(Long value) {
        return of(
                this.getCurrencyUnit(),
                this.getValue().multiply(new BigDecimal(value, this.getCurrency().mathContext()), this.getCurrency().mathContext())
        );
    }

    @Override
    public Money<C, U> divide(BigDecimal value) {
        return of(this.getCurrencyUnit(), this.getValue().divide(value, this.getCurrency().mathContext()));
    }

    @Override
    public Money<C, U> divide(Integer value) {
        return of(
                this.getCurrencyUnit(),
                this.getValue().divide(new BigDecimal(value, this.getCurrency().mathContext()), this.getCurrency().mathContext())
        );
    }

    @Override
    public Money<C, U> divide(Long value) {
        return of(
                this.getCurrencyUnit(),
                this.getValue().divide(new BigDecimal(value, this.getCurrency().mathContext()), this.getCurrency().mathContext())
        );
    }

    @Override
    public Money<C, U> sub(Money<C, U> other) {
        var otherValues = other.getCurrencyUnit().rateOf(this.getCurrencyUnit()).multiply(other.getValue(), this.getCurrency().mathContext());
        return of(this.getCurrencyUnit(), this.getValue().subtract(otherValues, this.getCurrency().mathContext()));
    }

    @Override
    public Money<? extends Currency, ? extends CurrencyUnit<? extends Currency>> sum(Money<? extends Currency, ? extends CurrencyUnit<? extends Currency>> money) {
        var otherValues = money.getCurrencyUnit().rateOf(this.getCurrencyUnit()).multiply(money.getValue(), getCurrency().mathContext());
        return of(getCurrencyUnit(), this.getValue().add(otherValues, getCurrency().mathContext()));
    }
}
