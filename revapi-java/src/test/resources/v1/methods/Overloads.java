public interface Overloads {

    void a();

    int a(int i);

    void a(int i, long l);

    void a(int i, long l, double d);

    void a(int i, long l, double d, float f);

    void b(Class<? extends Integer> x, Object y);

    void b(Object y);

    void c(Class<? extends Integer> x, Class<Long> y, int z);

    void c(Class<Long> x, Class<? extends Integer> y, float z);
}
