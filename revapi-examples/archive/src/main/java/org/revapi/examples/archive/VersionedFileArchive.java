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
package org.revapi.examples.archive;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.revapi.Archive;

/**
 * Generally speaking, archives are objects supplied by the caller of Revapi that represent the "files" that contain the
 * API elements.
 *
 * For Java, those would usually be the JAR files, for property files, those would probably be just the individual
 * files, etc.
 *
 * Implementing the `Archive` interface is therefore generally quite simple. The only decision we need to make is
 * whether we are able to also implement the {@link Archive.Versioned} interface, which then makes certain additional
 * features work.
 *
 * Let's implement a {@code VersionedFileArchive}. This will read the data from some file and the caller will just
 * instantiate this class with a certain version string.
 */
public class VersionedFileArchive implements Archive.Versioned {
    private final File file;
    private final String version;

    public VersionedFileArchive(File file, String version) {
        this.file = requireNonNull(file);
        this.version = requireNonNull(version);
    }

    @Override
    public String getName() {
        return file.getName();
    }

    @Override
    public InputStream openStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getBaseName() {
        return file.getName();
    }
}
