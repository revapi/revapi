package element.matcher;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public class MatchByKind {

    public static class Klass {}

    public static interface Iface {}

    public static enum Enm {
        CONSTANT
    }

    @Target(ElementType.METHOD)
    public static @interface Anno {

    }

    public int field;

    public <T> void method(int param) {

    }

    public static class GenericClass<T extends java.lang.Number> {
        public java.util.Map<String, ? extends T> genericMethod(T a1, Class<? super java.lang.Number> a2) {
            return null;
        }
    }
}