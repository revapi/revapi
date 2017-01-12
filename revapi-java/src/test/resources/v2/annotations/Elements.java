import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public class Elements {

    @Target({ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.METHOD,
            ElementType.PARAMETER, ElementType.TYPE, ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    public @interface Anno {

    }

    @Anno
    public static class TestClass<@Anno T extends @Anno Cloneable> {

        @Anno
        public <@Anno X extends @Anno String> int method(@Anno int param1, @Anno java.util.Map<@Anno X, @Anno String> param2) {
            return 0;
        }
    }
}
