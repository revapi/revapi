package comparison.methods;

import java.util.Set;

public interface ReturnType<R> {
    void method1();

    R method2();

    <U extends Comparable<U>> U method3();

    <U extends Number> U method4();

    Set<Integer> method5();

    Object method6();
}
