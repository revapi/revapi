/*
 * Copyright 2014-2023 Lukas Krejci
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.revapi.maven;

import java.util.Arrays;

/**
 * A complex type for capturing the "analysisConfigurationFiles" elements.
 *
 * @author Lukas Krejci
 *
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

    @Override
    public String toString() {
        return "ConfigurationFile{" + "path='" + path + '\'' + ", resource='" + resource + '\'' + ", roots="
                + Arrays.toString(roots) + '}';
    }
}
