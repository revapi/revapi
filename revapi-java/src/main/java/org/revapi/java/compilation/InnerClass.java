package org.revapi.java.compilation;

/**
 * @author Lukas Krejci
 * @since 0.2.0
 */
final class InnerClass {
    private final String binaryName;
    private final String canonicalName;

    public InnerClass(String binaryName, String canonicalName) {
        this.binaryName = binaryName;
        this.canonicalName = canonicalName;
    }

    public String getBinaryName() {
        return binaryName;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        InnerClass that = (InnerClass) o;

        return binaryName.equals(that.binaryName) && canonicalName.equals(that.canonicalName);
    }

    @Override
    public int hashCode() {
        int result = binaryName.hashCode();
        result = 31 * result + canonicalName.hashCode();
        return result;
    }
}
