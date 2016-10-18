public abstract class Abstract {

    public abstract void abstractMethod();

    public void concreteMethod() {}

    private static abstract class PrivateSuperClass {

    }

    private static abstract class PubliclyUsedPrivateSuperClass {

    }

    public static class A extends PrivateSuperClass {

    }

    public static abstract class B extends PrivateSuperClass {
        public abstract PubliclyUsedPrivateSuperClass abstractMethod();
    }

    public static class C extends PubliclyUsedPrivateSuperClass {

    }
}
