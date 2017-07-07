package element.matcher;

public class SubType {

    public interface Iface {

    }

    public class Base {

    }

    public class Child1 extends Base implements Iface {

    }

    public class Child2 extends Base {

    }

    public class GrandChild extends Child1 {

    }
}