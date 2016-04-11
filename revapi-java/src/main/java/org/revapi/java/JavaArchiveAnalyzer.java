/*
 * Copyright 2015 Lukas Krejci
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

import java.io.File;
import java.io.StringWriter;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nonnull;

import org.revapi.API;
import org.revapi.ArchiveAnalyzer;
import org.revapi.java.compilation.CompilationFuture;
import org.revapi.java.compilation.CompilationValve;
import org.revapi.java.compilation.Compiler;
import org.revapi.java.compilation.InclusionFilter;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.model.JavaElementForest;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class JavaArchiveAnalyzer implements ArchiveAnalyzer {
    private final API api;
    private final ExecutorService executor;
    private final ProbingEnvironment probingEnvironment;
    private final AnalysisConfiguration.MissingClassReporting missingClassReporting;
    private final boolean ignoreMissingAnnotations;
    private final boolean skipUseTracking;
    private final Set<File> bootstrapClasspath;
    private CompilationValve compilationValve;
    private InclusionFilter inclusionFilter;

    public JavaArchiveAnalyzer(API api, ExecutorService compilationExecutor,
            AnalysisConfiguration.MissingClassReporting missingClassReporting, boolean ignoreMissingAnnotations,
            boolean skipUseTracking, Set<File> bootstrapClasspath, InclusionFilter inclusionFilter) {
        this.api = api;
        this.executor = compilationExecutor;
        this.missingClassReporting = missingClassReporting;
        this.ignoreMissingAnnotations = ignoreMissingAnnotations;
        this.skipUseTracking = skipUseTracking;
        this.probingEnvironment = new ProbingEnvironment(api);
        this.bootstrapClasspath = bootstrapClasspath;
        this.inclusionFilter = inclusionFilter;
    }

    @Nonnull
    @Override
    public JavaElementForest analyze() {
        if (Timing.LOG.isDebugEnabled()) {
            Timing.LOG.debug("Starting analysis of " + api);
        }

        StringWriter output = new StringWriter();
        Compiler compiler = new Compiler(executor, output, api.getArchives(), api.getSupplementaryArchives());
        try {
            compilationValve = compiler
                .compile(probingEnvironment, missingClassReporting, ignoreMissingAnnotations, skipUseTracking,
                        bootstrapClasspath, inclusionFilter);

            probingEnvironment.getTree()
                .setCompilationFuture(new CompilationFuture(compilationValve, output));

            if (Timing.LOG.isDebugEnabled()) {
                Timing.LOG.debug("Preliminary API tree produced for " + api);
            }
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
