public class Inner {

    public static class StaticInnerClass {

    }

    public class InstanceInnerClass {

    }

    public class InstanceInnerClassWithCustomCtors {
        InstanceInnerClassWithCustomCtors() {}

        InstanceInnerClassWithCustomCtors(int blah){}
    }

    private static class Inaccessible {
        public static class Published {}
    }

    public Inaccessible.Published method() { return null; }
}
