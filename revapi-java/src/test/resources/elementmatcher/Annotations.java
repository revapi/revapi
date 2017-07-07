package element.matcher;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class Annotations {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface A {
        String value() default "";

        int arg1() default 0;

        int arg2();
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface B {
        String value() default "";
    }

    @A(arg2 = 1)
    @B
    public void method1() {}

    @B("kachna")
    public void method2() {}

    @A(value = "kachna", arg2 = 1)
    public void method3() {}

    @A(value = "kachna", arg1 = 0, arg2 = 1)
    public void method4() {}
}