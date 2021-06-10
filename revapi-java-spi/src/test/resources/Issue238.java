import java.io.Serializable;

public class Issue238 {
    public interface ValueSet extends Serializable {}
    public interface Range<T extends Comparable<? super T>> extends ValueSet {
        boolean contains(T value);
    }
    public class DefaultRange<T extends Comparable<? super T>> implements Range<T> {
        @Override
        public boolean contains(T value) {
            return false;
        }
    }
    public class MoneyRange<T extends Comparable<? super T>> extends DefaultRange<T> {
        @Override
        public boolean contains(T value) {
            return false;
        }
    }
}