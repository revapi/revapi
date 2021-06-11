import java.io.Serializable;
import java.util.Currency;
import java.lang.Comparable;

public class Issue238 {
    public interface ValueSet<T> extends Serializable {
        boolean contains(T value);
    }
    public interface Range<T extends Comparable<? super T>> extends ValueSet<T> {}
    public class DefaultRange<T extends Comparable<? super T>> implements Range<T> {
        
        private final T lower;
        private final T upper;
        private final T step;
        
        public DefaultRange() {
            lower = null;
            upper = null;
            step = null;
        }

        public DefaultRange(T lower, T upper, T step) {
            this.lower = lower;
            this.upper = upper;
            this.step = step;
        }

        @Override
        public boolean contains(T value) {
            return false;
        }
    }
    public class MoneyRange extends DefaultRange<Money> {
        
        public MoneyRange EMPTY = new MoneyRange();

        public MoneyRange() {
            super();
        }

        public MoneyRange(Money lower, Money upper, Money step) {
            super(lower, upper, step);
        }
        
        @Override
        public boolean contains(Money value) {
            return false;
        }
    }
    public class Money implements Comparable<Money>, Serializable {

        private final Double ammount;
        private final Currency currency;

        Money(Double ammount, Currency currency) {
            this.ammount = ammount;
            this.currency = currency;
        }

        public final Money valueOf(Double value, Currency currency) {
            return new Money(value, currency);
        } 

        @Override
        public int compareTo(Money other) {
            return 1;
        }
    }
}
