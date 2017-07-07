package element.matcher;

public class Throws {

    public void noThrow() {

    }

    public void singleThrow() throws java.lang.RuntimeException {

    }

    public void multipleThrow() throws java.lang.RuntimeException, java.io.IOException {
        throw new java.io.IOException();
    }
}