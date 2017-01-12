import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public class Elements {

    @Target({ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.METHOD,
            ElementType.PARAMETER, ElementType.TYPE, ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    public @interface Anno {

    }

    public static class TestClass<T extends Cloneable> {

        public <X extends String> int method(int param1, java.util.Map<X, String> param2) {
            return 0;
        }
    }
}
