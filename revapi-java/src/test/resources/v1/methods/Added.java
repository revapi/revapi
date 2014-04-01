public final class Added {

    public interface Iface {

    }

    public static abstract class Abstract {
        public void ordinaryMethod1() {}
        public abstract void ordinaryMethod2();
        public final void ordinaryMethod3(){}
    }

    public static abstract class Ordinary extends Abstract {

    }
}
