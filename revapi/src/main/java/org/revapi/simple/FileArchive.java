/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.revapi.simple;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nonnull;

import org.revapi.Archive;

/**
 * A simple implementation of the {@link Archive} interface providing a file as a Revapi
 * archive.
 *
 * @author Lukas Krejci
 * @since 0.4.1
 */
public class FileArchive implements Archive {
    private final File file;

    public FileArchive(File file) {
        this.file = file;
    }

    @Override
    public @Nonnull String getName() {
        return file.getName();
    }

    @Override
    public @Nonnull InputStream openStream() throws IOException {
        return new FileInputStream(file);
    }
}
