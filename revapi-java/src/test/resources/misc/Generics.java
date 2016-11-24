public class Generics<T extends GenericsParams.TypeVar & GenericsParams.TypeVarIface, U extends Generics<GenericsParams.TypeVarImpl, ?>> {

    public java.util.Set<GenericsParams.TypeParam> field;

    public <X extends U> X method1() {return null;}

    public void method2(java.util.Set<? super GenericsParams.SuperBound> x) {}

    public <X extends T> X method3() {return null;}

    public <X extends GenericsParams.ExtendsBound> void method4(java.util.Set<X> x) {}
}
