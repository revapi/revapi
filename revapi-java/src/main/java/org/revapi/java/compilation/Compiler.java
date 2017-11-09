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
package org.revapi.java.compilation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.revapi.Archive;
import org.revapi.java.AnalysisConfiguration;
import org.revapi.java.Timing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Compiler {
    private static final Logger LOG = LoggerFactory.getLogger(Compiler.class);

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
                                    final AnalysisConfiguration.MissingClassReporting missingClassReporting,
                                    final boolean ignoreMissingAnnotations,
                                    final InclusionFilter inclusionFilter)
            throws Exception {

        File targetPath = Files.createTempDirectory("revapi-java").toAbsolutePath().toFile();

        File sourceDir = new File(targetPath, "sources");
        sourceDir.mkdir();

        File lib = new File(targetPath, "lib");
        lib.mkdir();

        // make sure the classpath is in the same order as passed in
        int classPathSize = size(classPath);
        int nofArchives = classPathSize + size(additionalClassPath);

        int prefixLength = (int) Math.log10(nofArchives) + 1;

        IdentityHashMap<Archive, File> classPathFiles = copyArchives(classPath, lib, 0, prefixLength);
        IdentityHashMap<Archive, File> additionClassPathFiles = copyArchives(additionalClassPath, lib, classPathSize, prefixLength);

        List<String> options = Arrays.asList(
            "-d", sourceDir.toString(),
            "-cp", composeClassPath(lib)
        );

        List<JavaFileObject> sources = Arrays.<JavaFileObject>asList(
            new MarkerAnnotationObject(),
            new ArchiveProbeObject()
        );

        //the locale and charset are actually not important, because the only sources we're providing
        //are not file-based. The rest of the stuff the compiler will be touching is already compiled
        //and therefore not affected by the charset.
        StandardJavaFileManager fileManager = compiler
                .getStandardFileManager(null, Locale.getDefault(), Charset.forName("UTF-8"));

        final JavaCompiler.CompilationTask task = compiler
            .getTask(output, fileManager, null, options, Collections.singletonList(ArchiveProbeObject.CLASS_NAME),
                sources);

        ProbingAnnotationProcessor processor = new ProbingAnnotationProcessor(environment);

        task.setProcessors(Collections.singletonList(processor));

        Future<Boolean> future = processor.submitWithCompilationAwareness(executor, task, () -> {
            if (Timing.LOG.isDebugEnabled()) {
                Timing.LOG.debug("About to crawl " + environment.getApi());
            }

            try {
                new ClasspathScanner(fileManager, environment, classPathFiles, additionClassPathFiles,
                        missingClassReporting, ignoreMissingAnnotations, inclusionFilter).initTree();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to scan the classpath.", e);
            }

            if (Timing.LOG.isDebugEnabled()) {
                Timing.LOG.debug("Crawl finished for " + environment.getApi());
            }
        });


        return new CompilationValve(future, targetPath, environment, fileManager);
    }

    private String composeClassPath(File classPathDir) {
        StringBuilder bld = new StringBuilder();

        File[] jars = classPathDir.listFiles();

        if (jars == null || jars.length == 0) {
            return "";
        }

        List<File> sortedJars = new ArrayList<>(Arrays.asList(jars));
        Collections.sort(sortedJars, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        Iterator<File> it = sortedJars.iterator();

        bld.append(it.next().getAbsolutePath());
        while (it.hasNext()) {
            bld.append(File.pathSeparator).append(it.next().getAbsolutePath());
        }

        return bld.toString();
    }

    private IdentityHashMap<Archive, File>
    copyArchives(Iterable<? extends Archive> archives, File parentDir, int startIdx, int prefixLength) {
        IdentityHashMap<Archive, File> ret = new IdentityHashMap<>();
        if (archives == null) {
            return ret;
        }

        for (Archive a : archives) {
            String name = formatName(startIdx++, prefixLength, a.getName());
            File f = new File(parentDir, name);

            ret.put(a, f);

            if (f.exists()) {
                LOG.warn(
                    "File " + f.getAbsolutePath() + " with the data of archive '" + a.getName() + "' already exists." +
                            " Assume it already contains the bits we need.");
                continue;
            }

            Path target = new File(parentDir, name).toPath();

            try (InputStream data = a.openStream()) {
                Files.copy(data, target);
            } catch (IOException e) {
                throw new IllegalStateException(
                    "Failed to copy class path element: " + a.getName() + " to " + f.getAbsolutePath(), e);
            }
        }

        return ret;
    }

    private int size(Iterable<?> collection) {
        if (collection == null) {
            return 0;
        }

        int ret = 0;
        Iterator<?> it = collection.iterator();
        while (it.hasNext()) {
            ret++;
            it.next();
        }

        return ret;
    }

    private String formatName(int idx, int prefixLength, String rootName) {
        try {
            return String.format("%0" + prefixLength + "d-%s", idx, UUID.nameUUIDFromBytes(rootName.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 not supported.");
        }
    }
}
