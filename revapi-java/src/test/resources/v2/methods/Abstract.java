public abstract class Abstract {

    public void abstractMethod() {}

    public abstract void concreteMethod();

    private static abstract class PrivateSuperClass {
        //this won't be reported as anything, because this class is private and all its public
        //inheritors are concrete and/or implement this method.
        public abstract void method();
    }

    private static abstract class PubliclyUsedPrivateSuperClass {
        public abstract void method();
    }

    public static class A extends PrivateSuperClass {
        public void method() {}
    }

    public static abstract class B extends PrivateSuperClass {
        public void method() {}
        public abstract PubliclyUsedPrivateSuperClass abstractMethod();
    }

    public static class C extends PubliclyUsedPrivateSuperClass {
        public void method() {}
    }
}
