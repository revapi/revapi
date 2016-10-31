public interface DefaultMethod {
    default void a() {

    }

    int b();

    class Test implements DefaultMethod {
        public int b() {
            return 0;
        }
    }
}
