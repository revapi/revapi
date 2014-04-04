package comparison.methods;

import java.util.Set;

public interface ReturnType<R extends Number> {
    int method1();

    R method2();

    <U extends Comparable<R>> U method3();

    <U extends R> U method4();

    Set<Double> method5();

    String method6();
}
