public interface DefaultMethod {
    void a();

    default int b() {
        return 0;
    }

    default void c() {}
}
