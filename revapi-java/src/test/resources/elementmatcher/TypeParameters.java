package element.matcher;

public class TypeParameters {

    public static class Base<T> {
        public T method() {
            return null;
        }
    }

    public static class ConcreteChild extends Base<Integer> {

    }

    public static class GenericChild<T extends String, U> extends Base<T> {

        public <V extends T> U genericMethod(V param) {
            return null;
        }
    }
}