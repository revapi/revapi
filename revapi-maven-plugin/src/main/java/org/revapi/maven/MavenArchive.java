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
package org.revapi.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nonnull;

import org.eclipse.aether.artifact.Artifact;
import org.revapi.Archive;

/**
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
class MavenArchive implements Archive.Versioned {

    private final File file;
    private final String gav;
    private final String version;
    private final String ga;

    private MavenArchive(Artifact artifact) {
        if (artifact == null) {
            throw new IllegalArgumentException("Artifact cannot be null");
        }

        file = artifact.getFile();
        if (file == null) {
            throw new IllegalArgumentException("Could not locate the file of the maven artifact: " + artifact);
        }

        this.gav = artifact.toString();
        this.version = artifact.getBaseVersion();

        if (gav.endsWith(version)) {
            ga = gav.substring(0, gav.length() - version.length() - 1);
        } else {
            ga = gav;
        }
    }

    public static MavenArchive of(Artifact artifact) {
        if ("pom".equals(artifact.getExtension())) {
            return new Empty(artifact);
        } else {
            return new MavenArchive(artifact);
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return gav;
    }

    @Nonnull
    @Override
    public InputStream openStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public @Nonnull String getVersion() {
        return version;
    }

    @Override
    public String getBaseName() {
        return ga;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MavenArchive that = (MavenArchive) o;

        return file.equals(that.file);
    }

    @Override
    public int hashCode() {
        return file.hashCode();
    }

    @Override
    public String toString() {
        return "MavenArchive[gav=" + gav + ", file=" + file + ']';
    }

    public static final class Empty extends MavenArchive {

        public Empty(Artifact artifact) {
            super(artifact);
        }

        @Nonnull
        @Override
        public InputStream openStream() throws IOException {
            return new InputStream() {
                @Override
                public int read() throws IOException {
                    return -1;
                }
            };
        }
    }
}
