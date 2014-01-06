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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import org.revapi.Archive;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class Compiler {
    private final JavaCompiler compiler;
    private final Writer output;
    private final Iterable<Archive> classPath;
    private final ExecutorService executor;

    public Compiler(ExecutorService executor, Writer reportingOutput, Iterable<Archive> classPath) {
        compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new AssertionError("Could not obtain the system compiler. Is tools.jar on the classpath?");
        }

        this.executor = executor;
        this.output = reportingOutput;
        this.classPath = classPath;
    }

    public Future<Boolean> compile(ProbingEnvironment environment) throws Exception {
        File targetPath = Files.createTempDirectory("revapi-java").toAbsolutePath().toFile();

        File sourceDir = new File(targetPath, "sources");
        sourceDir.mkdir();

        File lib = new File(targetPath, "lib");
        lib.mkdir();

        copyClassPathTo(lib);

        List<String> options = Arrays.asList(
            "-d", sourceDir.toString(),
            "-cp", composeClassPath(lib)
        );

        List<JavaFileObject> sources = Arrays.<JavaFileObject>asList(
            new MarkerAnnotationObject(),
            new ArchiveProbeObject(classPath, environment)
        );

        final JavaCompiler.CompilationTask task = compiler
            .getTask(output, null, null, options, Arrays.asList(ArchiveProbeObject.CLASS_NAME),
                sources);

        ProbingAnnotationProcessor processor = new ProbingAnnotationProcessor(environment);

        task.setProcessors(Arrays.asList(processor));

        return processor.waitForProcessingAndExecute(executor, new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return task.call();
                //TODO cleanup of the compiler output (possibly wait until after analysis? How does javac load
                //model classes?
            }
        });
    }

    private String composeClassPath(File classPathDir) {
        StringBuilder bld = new StringBuilder();

        Iterator<Archive> it = classPath.iterator();

        if (!it.hasNext()) {
            return "";
        }

        bld.append(new File(classPathDir, it.next().getName()).getAbsolutePath());

        while (it.hasNext()) {
            bld.append(File.pathSeparator).append(new File(classPathDir, it.next().getName()).getAbsolutePath());
        }

        return bld.toString();
    }

    private void copyClassPathTo(File parentDir) {
        for (Archive a : classPath) {
            Path target = new File(parentDir, a.getName()).toPath();

            try (InputStream data = a.openStream()) {
                Files.copy(data, target);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to copy class path element: " + a.getName());
            }
        }
    }
}
