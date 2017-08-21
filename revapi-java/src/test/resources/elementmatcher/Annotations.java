package element.matcher;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class Annotations {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface A {
        String[] value() default {};

        int arg1() default 0;

        int arg2();

        B[] arg3() default {};
    }

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    public @interface B {
        String value() default "kachna";
    }

    @A(arg2 = 1)
    @B
    public void method1() {}

    @B("kachna")
    public void method2() {}

    @A(arg2 = 1)
    public void method3() {}

    @A(value = {"kachna", "drachma"}, arg1 = 0, arg2 = 0, arg3 = @B)
    public void method4() {}

    @B
    public class Base {

    }

    @B
    public interface Iface {

    }

    public class InheritingChild extends Base {

    }

    public class NotInheritingChild implements Iface {

    }
}