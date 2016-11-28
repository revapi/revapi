import java.lang.annotation.Documented;

public class Downplayed {

    @Documented
    public @interface Annotation {

    }

    @FunctionalInterface
    public interface Functional {
        void method();
    }
}
