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

import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.ApiAnalyzer;
import org.revapi.ArchiveAnalyzer;
import org.revapi.DifferenceAnalyzer;
import org.revapi.java.compilation.CompilationValve;
import org.revapi.java.compilation.ProbingEnvironment;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class JavaApiAnalyzer implements ApiAnalyzer {

    private final ExecutorService compilationExecutor = Executors.newFixedThreadPool(2);
    private AnalysisContext analysisContext;
    private final Iterable<Check> checks;

    public JavaApiAnalyzer() {
        this(ServiceLoader.load(Check.class, JavaApiAnalyzer.class.getClassLoader()));
    }

    public JavaApiAnalyzer(Iterable<Check> checks) {
        this.checks = checks;
    }

    @Nullable
    @Override
    public String[] getConfigurationRootPaths() {
        ArrayList<String> checkConfigPaths = new ArrayList<>();
        for (Check c : checks) {
            String[] cp = c.getConfigurationRootPaths();
            if (cp != null) {
                checkConfigPaths.addAll(Arrays.asList(cp));
            }
        }

        if (checkConfigPaths.isEmpty()) {
            return null;
        } else {
            String[] configs = new String[checkConfigPaths.size()];
            configs = checkConfigPaths.toArray(configs);

            return configs;
        }
    }

    @Nullable
    @Override
    public Reader getJSONSchema(@Nonnull String configurationRootPath) {
        for (Check check : checks) {
            String[] roots = check.getConfigurationRootPaths();
            if (roots == null) {
                continue;
            }

            for (String root : check.getConfigurationRootPaths()) {
                if (configurationRootPath.equals(root)) {
                    return check.getJSONSchema(root);
                }
            }
        }

        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        this.analysisContext = analysisContext;
    }

    @Nonnull
    @Override
    public ArchiveAnalyzer getArchiveAnalyzer(@Nonnull API api) {
        return new JavaArchiveAnalyzer(api, compilationExecutor);
    }

    @Nonnull
    @Override
    public DifferenceAnalyzer getDifferenceAnalyzer(@Nonnull ArchiveAnalyzer oldArchive,
        @Nonnull ArchiveAnalyzer newArchive) {
        JavaArchiveAnalyzer oldA = (JavaArchiveAnalyzer) oldArchive;
        JavaArchiveAnalyzer newA = (JavaArchiveAnalyzer) newArchive;

        ProbingEnvironment oldEnvironment = oldA.getProbingEnvironment();
        ProbingEnvironment newEnvironment = newA.getProbingEnvironment();
        CompilationValve oldValve = oldA.getCompilationValve();
        CompilationValve newValve = newA.getCompilationValve();

        return new JavaElementDifferenceAnalyzer(analysisContext, oldEnvironment, oldValve, newEnvironment, newValve,
            checks);
    }

    @Override
    public void close() {
    }
}
