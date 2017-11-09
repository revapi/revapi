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
package org.revapi.ant;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nonnull;

import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.revapi.Archive;

/**
 * @author Lukas Krejci
 * @since 0.2
 */
final class FileArchive implements Archive {

    public static FileArchive[] from(FileSet fileSet) {
        if (fileSet == null) {
            return new FileArchive[0];
        }

        File[] files = scanFileSet(fileSet);
        FileArchive[] ret = new FileArchive[files.length];

        for (int i = 0; i < files.length; ++i) {
            ret[i] = new FileArchive(files[i]);
        }

        return ret;
    }

    private final File file;

    FileArchive(File file) {
        this.file = file;
    }

    @Nonnull
    @Override
    public String getName() {
        return file.getName();
    }

    @Nonnull
    @Override
    public InputStream openStream() throws IOException {
        return new FileInputStream(file);
    }

    private static File[] scanFileSet(FileSet fs) {
        Project prj = fs.getProject();
        DirectoryScanner scanner = fs.getDirectoryScanner(prj);
        scanner.scan();
        File basedir = scanner.getBasedir();
        String[] fileNames = scanner.getIncludedFiles();
        File[] ret = new File[fileNames.length];
        for (int i = 0; i < fileNames.length; i++) {
            String fileName = fileNames[i];
            ret[i] = new File(basedir, fileName);
        }
        return ret;
    }
}
