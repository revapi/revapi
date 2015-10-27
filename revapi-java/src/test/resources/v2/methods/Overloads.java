public interface Overloads {

    //void a();
    double a();

    //int a(int i);
    double a(int i);

    //void a(int i, long l);
    void a(long l, int i);

    void a(int i, long l, double d);

    //void a(int i, long l, double d, float f);
    void a(int i, long l, float f);

    void b(Class<?> x, Object y);

    void b(Object y);

    void c(Class<? extends Integer> x, Class<?> y, int z);

    void c(Class<Long> x, int y, float z);
}
