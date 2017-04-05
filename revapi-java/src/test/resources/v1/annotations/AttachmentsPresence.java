import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Target;

public class AttachmentsPresence {

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
    @Inherited
    public @interface Anno1 {
        String value() default "";
    }

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
    public @interface Anno2 {

    }

    public void method1() {

    }

    @Deprecated
    public void method2() {

    }

    @Anno1("a")
    public void method3(@Anno1 int a) {

    }
}
