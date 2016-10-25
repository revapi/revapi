public class CovariantReturnTypeAndInheritance {

    private static class Base<T> {
        public Object method() {
            return null;
        }

        public T genericMethod() {
            return null;
        }

        public Number nonGenericMethod() {
            return null;
        }
    }

    public static class Class<E extends Number> extends Base<E> {
        public E genericMethod() {
            return null;
        }

        public E nonGenericMethod() {
            return null;
        }
    }
}
