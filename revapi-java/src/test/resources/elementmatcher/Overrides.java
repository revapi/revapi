package element.matcher;

public class Overrides {

    public static class Base {

        public Number baseMethod(int i) {
            return null;
        }
    }

    public interface Iface {

        void ifaceMethod(String s);
    }

    public static class Check extends Base implements Iface {

        public Integer baseMethod(int i) {
            return null;
        }

        public void ifaceMethod(String s) {

        }

        public void myMethod() {

        }
    }
}