package org.revapi.maven;

/**
 * A complex type for capturing the "analysisConfigurationFiles" elements.
 *
 * @author Lukas Krejci
 * @since 0.2.0
 */
public final class ConfigurationFile {

    private String path;
    private String resource;
    private String[] roots;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String classPath) {
        this.resource = classPath;
    }

    public String[] getRoots() {
        return roots;
    }

    public void setRoots(String[] roots) {
        this.roots = roots;
    }
}
