public class Generics {

    public static abstract class A<T> {
        public T field;
    }

    public static class B extends A<Integer> {}

    public static class C<T extends Number> extends A<T> {}
}
