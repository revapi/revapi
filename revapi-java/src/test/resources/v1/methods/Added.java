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

    //check sensitivity to method name length and numbers at the end of the method name
    //these are the same in both versions. The check is that they should not cause any changes
    public void getContentOfSection() {}
    public void getContentOfSectionAB() {}
    public void getContentOfSection10() {}
}
