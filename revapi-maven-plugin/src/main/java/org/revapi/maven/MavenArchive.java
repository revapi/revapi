/*
 * Copyright 2014-2017 Lukas Krejci
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nonnull;

import org.eclipse.aether.artifact.Artifact;
import org.revapi.Archive;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
class MavenArchive implements Archive.Versioned {

    private final File file;
    private final String gav;
    private final String version;

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
    }

    public static MavenArchive of(Artifact artifact) {
        switch (artifact.getExtension()) {
            case "war":
                return new War(artifact);
            case "ear":
                return new Empty(artifact);
            case "pom":
                return new Empty(artifact);
            default:
                return new Jar(artifact);
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

    public static final class Jar extends MavenArchive {

        public Jar(Artifact artifact) {
            super(artifact);
        }
    }

    public static final class War extends MavenArchive {

        public War(Artifact artifact) {
            super(artifact);
        }

        @Nonnull
        @Override
        public InputStream openStream() throws IOException {
            final Path path = Files.createTempFile("revapi-maven-plugin", null);

            try (ZipInputStream warZip = new ZipInputStream(super.openStream());
                 ZipOutputStream croppedZip = new ZipOutputStream(new FileOutputStream(path.toFile()))) {

                croppedZip.setLevel(Deflater.NO_COMPRESSION);
                croppedZip.setMethod(ZipOutputStream.DEFLATED);

                byte[] buf = new byte[32768];

                ZipEntry inEntry = warZip.getNextEntry();
                int prefixLen = "WEB-INF/classes/".length();
                while (inEntry != null) {
                    if (inEntry.getName().startsWith("WEB-INF/classes/") && inEntry.getName().length() > prefixLen) {
                        ZipEntry outEntry = new ZipEntry(inEntry.getName().substring(prefixLen));

                        croppedZip.putNextEntry(outEntry);

                        if (!inEntry.isDirectory()) {
                            int cnt;
                            while ((cnt = warZip.read(buf)) != -1) {
                                croppedZip.write(buf, 0, cnt);
                            }
                        }

                        croppedZip.closeEntry();
                    }

                    inEntry = warZip.getNextEntry();
                }
            }

            return new FileInputStream(path.toFile()) {
                @Override
                public void close() throws IOException {
                    super.close();
                    Files.delete(path);
                }
            };
        }
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
