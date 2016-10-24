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

    public static class Class extends Base<Number> {
        //covariant return type - this should be considered an override
        public String method() {
            return null;
        }

        //adding a parameter should make this no longer override the method from base
        public Number genericMethod(int i) {
            return null;
        }
    }
}
