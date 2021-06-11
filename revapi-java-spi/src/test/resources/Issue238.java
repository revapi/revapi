import java.io.Serializable;

public class Issue238 {
    public interface ValueSet<T> extends Serializable {
        boolean contains(T value);
    }
    public interface Range<T extends Comparable<? super T>> extends ValueSet<T> {}
    public class DefaultRange<T extends Comparable<? super T>> implements Range<T> {
        @Override
        public boolean contains(T value) {
            return false;
        }
    }
    public class MoneyRange extends DefaultRange<Money> {
        @Override
        public boolean contains(Money value) {
            return false;
        }
    }
    public class Money implements Comparable<Money>, Serializable {
        @Override
        public int compareTo(Money other) {
            return 1;
        }
    }
}
