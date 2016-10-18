public class Generics {

    public static abstract class A<T> {
        public T field;
    }

    public static class B extends A<String> {}

    public static class C extends A<Number> {}
}
