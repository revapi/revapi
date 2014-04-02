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

package org.revapi.java.compilation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import org.revapi.Archive;
import org.revapi.java.model.ClassTreeInitializer;
import org.revapi.java.model.MissingClassReporting;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Compiler {
    private final JavaCompiler compiler;
    private final Writer output;
    private final Iterable<? extends Archive> classPath;
    private final Iterable<? extends Archive> additionalClassPath;
    private final ExecutorService executor;

    public Compiler(ExecutorService executor, Writer reportingOutput, Iterable<? extends Archive> classPath,
        Iterable<? extends Archive> additionalClassPath) {

        compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new AssertionError("Could not obtain the system compiler. Is tools.jar on the classpath?");
        }

        this.executor = executor;
        this.output = reportingOutput;
        this.classPath = classPath;
        this.additionalClassPath = additionalClassPath;
    }

    public CompilationValve compile(final ProbingEnvironment environment,
        final MissingClassReporting missingClassReporting) throws Exception {

        File targetPath = Files.createTempDirectory("revapi-java").toAbsolutePath().toFile();

        File sourceDir = new File(targetPath, "sources");
        sourceDir.mkdir();

        File lib = new File(targetPath, "lib");
        lib.mkdir();

        copyArchives(classPath, lib);
        copyArchives(additionalClassPath, lib);

        List<String> options = Arrays.asList(
            "-d", sourceDir.toString(),
            "-cp", composeClassPath(lib)
        );

        List<JavaFileObject> sources = Arrays.<JavaFileObject>asList(
            new MarkerAnnotationObject(),
            new ArchiveProbeObject()
        );

        final JavaCompiler.CompilationTask task = compiler
            .getTask(output, null, null, options, Arrays.asList(ArchiveProbeObject.CLASS_NAME),
                sources);

        ProbingAnnotationProcessor processor = new ProbingAnnotationProcessor(environment);

        task.setProcessors(Arrays.asList(processor));

        Future<Boolean> future = processor.submitWithCompilationAwareness(executor, new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                new ClassTreeInitializer(environment, missingClassReporting).initTree();

                return task.call();
            }
        });

        return new CompilationValve(future, targetPath, environment);
    }

    private String composeClassPath(File classPathDir) {
        StringBuilder bld = new StringBuilder();

        File[] jars = classPathDir.listFiles();

        if (jars.length == 0) {
            return "";

        }

        bld.append(jars[0].getAbsolutePath());
        for (int i = 1; i < jars.length; ++i) {
            bld.append(File.pathSeparator).append(jars[i].getAbsolutePath());
        }

        return bld.toString();
    }

    private void copyArchives(Iterable<? extends Archive> archives, File parentDir) {
        if (archives == null) {
            return;
        }

        for (Archive a : archives) {
            Path target = new File(parentDir, a.getName()).toPath();

            try (InputStream data = a.openStream()) {
                Files.copy(data, target);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to copy class path element: " + a.getName());
            }
        }
    }
}
