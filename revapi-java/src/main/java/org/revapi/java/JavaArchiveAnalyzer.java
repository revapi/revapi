/*
 * Copyright 2014 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.revapi.java;

import java.io.StringWriter;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nonnull;

import org.revapi.API;
import org.revapi.ArchiveAnalyzer;
import org.revapi.java.compilation.CompilationFuture;
import org.revapi.java.compilation.CompilationValve;
import org.revapi.java.compilation.Compiler;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.model.JavaElementForest;
import org.revapi.java.model.MissingClassReporting;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class JavaArchiveAnalyzer implements ArchiveAnalyzer {
    private final API api;
    private final ExecutorService executor;
    private final ProbingEnvironment probingEnvironment;
    private final MissingClassReporting missingClassReporting;
    private CompilationValve compilationValve;

    public JavaArchiveAnalyzer(API api, ExecutorService compilationExecutor,
        MissingClassReporting missingClassReporting) {
        this.api = api;
        this.executor = compilationExecutor;
        this.missingClassReporting = missingClassReporting;
        this.probingEnvironment = new ProbingEnvironment(api);
    }

    @Nonnull
    @Override
    public JavaElementForest analyze() {
        StringWriter output = new StringWriter();
        Compiler compiler = new Compiler(executor, output, api.getArchives(), api.getSupplementaryArchives());
        try {
            compilationValve = compiler.compile(probingEnvironment, missingClassReporting);

            probingEnvironment.getTree()
                .setCompilationFuture(new CompilationFuture(compilationValve, output));

            return probingEnvironment.getTree();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to analyze archives in api " + api, e);
        }
    }

    public ProbingEnvironment getProbingEnvironment() {
        return probingEnvironment;
    }

    public CompilationValve getCompilationValve() {
        return compilationValve;
    }
}
