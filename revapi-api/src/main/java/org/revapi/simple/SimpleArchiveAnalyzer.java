/*
 * Copyright 2013 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi.simple;

import java.io.IOException;
import java.io.InputStream;

import org.revapi.Archive;
import org.revapi.ArchiveAnalyzer;
import org.revapi.Tree;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public abstract class SimpleArchiveAnalyzer<Lang, Tr extends Tree> implements ArchiveAnalyzer {
    private final Archive archive;
    private InputStream stream;

    protected SimpleArchiveAnalyzer(Archive archive) {
        this.archive = archive;
    }

    protected String getName() {
        return archive.getName();
    }

    protected InputStream openStream() throws IOException {
        close();

        stream = archive.openStream();
        return stream;
    }

    @Override
    public final Tr analyze() {
        try {
            return doAnalyze();
        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze " + getName(), e);
        }
    }

    protected abstract Tr doAnalyze() throws Exception;

    @Override
    public void close() throws IOException {
        if (stream != null) {
            stream.close();
            stream = null;
        }
    }
}
