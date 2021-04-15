/*
 * Copyright 2014-2021 Lukas Krejci
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
package org.revapi;

import java.io.IOException;
import java.io.InputStream;

/**
 * A simple abstraction of a file or archive. The archive merely has a name and can be opened as a stream.
 *
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public interface Archive {
    String getName();

    InputStream openStream() throws IOException;

    /**
     * Extension of the archive interface that can also provide the version of the archive. This can be used by certain
     * extensions like the {@code SemverIgnoreTransform}.
     * <p>
     * Note that it is the responsibility of the caller of Revapi to provide archives which implement this interface.
     *
     * @since 0.4.1
     */
    interface Versioned extends Archive {
        String getVersion();

        /**
         * @return the name of the archive without the version (if {@link #getName()} contains the version in the name).
         */
        String getBaseName();
    }

    /**
     * The role of the archive in the API. See {@link API#getArchiveRole(Archive)}
     */
    enum Role {
        PRIMARY, SUPPLEMENTARY, UNKNOWN
    }
}
