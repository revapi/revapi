import java.io.IOException;

public class ExceptionsAndInheritance {

    private static abstract class Base {
        public abstract void abstractChecked() throws IOException;
        public abstract void abstractUnchecked();

        public void concreteChecked() throws IOException {
            throw new IOException();
        }
        public void concreteUnchecked() throws IllegalArgumentException {
        }
    }

    public static class ChildWithNoExceptions extends Base {
        public void abstractChecked() {}
        public void abstractUnchecked() throws IllegalArgumentException {}
    }

    public static class ChildWithSpecializedExceptions extends Base {
        public void abstractChecked() throws java.io.FileNotFoundException {
            throw new java.io.FileNotFoundException();
        }

        public void abstractUnchecked() {}
    }
}
