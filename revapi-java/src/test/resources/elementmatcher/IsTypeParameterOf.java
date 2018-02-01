package element.matcher;

public class IsTypeParameterOf {

    public class X {

    }

    public class C<T extends IsTypeParameterOf> {
        public <U extends X> void method2(T t, U u) {

        }
    }

    public <T extends C<? extends IsTypeParameterOf>> void method1() {

    }
}
