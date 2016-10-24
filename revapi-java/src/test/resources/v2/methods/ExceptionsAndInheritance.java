import java.io.IOException;

public class ExceptionsAndInheritance {

    private static abstract class Base {
        public abstract void abstractChecked() throws Exception;
        public abstract void abstractUnchecked() throws IllegalArgumentException;

        public void concreteChecked() throws IOException {
            throw new IOException();
        }
        public void concreteUnchecked() {
        }
    }

    public static class ChildWithNoExceptions extends Base {
        public void abstractChecked() {}
        public void abstractUnchecked() {}

        public void concreteChecked() {}
        public void concreteUnchecked() throws IllegalArgumentException {}
    }

    public static class ChildWithSpecializedExceptions extends Base {
        public void abstractChecked() throws java.io.FileNotFoundException {
            throw new java.io.FileNotFoundException();
        }

        public void abstractUnchecked() throws IllegalStateException {}
    }
}
