package element.matcher;

public interface IsArgumentOf {

    void none();

    void a(A a);

    void b(B b);

    void c(C c);

    void d(A a, B b);

    void e(A a, C c);

    void f(A a, B b, C c);

    class A {

    }

    class B {

    }

    class C {

    }
}
