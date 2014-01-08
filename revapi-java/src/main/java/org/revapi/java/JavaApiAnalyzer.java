/*
 * Copyright 2014 Lukas Krejci
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

package org.revapi.java;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.revapi.ApiAnalyzer;
import org.revapi.Archive;
import org.revapi.ArchiveAnalyzer;
import org.revapi.Configuration;
import org.revapi.ElementAnalyzer;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class JavaApiAnalyzer implements ApiAnalyzer {

    private final ExecutorService compilationExecutor = Executors.newFixedThreadPool(2);
    private Configuration configuration;

    @Override
    public void initialize(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public ArchiveAnalyzer getArchiveAnalyzer(Iterable<Archive> archives) {
        return new JavaArchiveAnalyzer(configuration, archives, compilationExecutor);
    }

    @Override
    public ElementAnalyzer getElementAnalyzer(ArchiveAnalyzer oldArchive, ArchiveAnalyzer newArchive) {
        return new JavaElementAnalyzer(configuration, ((JavaArchiveAnalyzer) oldArchive).getProbingEnvironment(),
            ((JavaArchiveAnalyzer) newArchive).getProbingEnvironment());
    }
}
