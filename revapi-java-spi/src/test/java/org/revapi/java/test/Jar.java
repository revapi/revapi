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

package org.revapi.java.test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * @author Lukas Krejci
 * @since 0.2
 */
public class Jar implements TestRule {

    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    private List<EnvironmentImpl> compiledStuff = new ArrayList<>();

    private ExecutorService compileProcess = Executors.newCachedThreadPool();

    @Override
    public Statement apply(final Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                compiledStuff.clear();
                try {
                    base.evaluate();
                } finally {
                    cleanUp();
                }
            }
        };
    }

    public Environment compile(String... sources) throws Exception {

        File dir = Files.createTempDirectory("revapi-java-spi").toFile();

        List<JavaFileObject> sourceObjects = new ArrayList<>();
        sourceObjects.add(new MarkerAnnotationObject());
        sourceObjects.add(new ArchiveProbeObject());
        for (String s : sources) {
            sourceObjects.add(new SourceInClassLoader(s));
        }

        List<String> options = Arrays.asList("-d", dir.getAbsolutePath());

        final JavaCompiler.CompilationTask task = compiler
            .getTask(null, null, null, options, Arrays.asList(ArchiveProbeObject.CLASS_NAME), sourceObjects);

        final Semaphore cleanUpSemaphore = new Semaphore(0);
        final Semaphore initSemaphore = new Semaphore(0);

        final EnvironmentImpl ret = new EnvironmentImpl();

        task.setProcessors(Arrays.asList(new AbstractProcessor() {
            @Override
            public SourceVersion getSupportedSourceVersion() {
                return SourceVersion.RELEASE_7;
            }

            @Override
            public Set<String> getSupportedAnnotationTypes() {
                return new HashSet<>(Arrays.asList(MarkerAnnotationObject.CLASS_NAME));
            }

            @Override
            public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                if (roundEnv.processingOver()) {
                    ret.elements = processingEnv.getElementUtils();
                    ret.types = processingEnv.getTypeUtils();

                    initSemaphore.release();

                    try {
                        cleanUpSemaphore.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    return true;
                }

                return false;
            }
        }));

        compileProcess.submit(new Runnable() {
            @Override
            public void run() {
                task.call();
            }
        });

        initSemaphore.acquire();

        ret.semaphore = cleanUpSemaphore;
        ret.dir = dir;

        return ret;
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "ConstantConditions"})
    private void cleanUp() {
        for (EnvironmentImpl e : compiledStuff) {
            e.semaphore.release();
            for (File f : e.dir.listFiles()) {
                f.delete();
            }
            e.dir.delete();
        }
    }

    public interface Environment {
        Elements getElementUtils();

        Types getTypeUtils();
    }

    private static class EnvironmentImpl implements Environment {
        private Elements elements;
        private Types types;
        private Semaphore semaphore;
        private File dir;

        @Override
        public Elements getElementUtils() {
            return elements;
        }

        @Override
        public Types getTypeUtils() {
            return types;
        }
    }

    private static class SourceInClassLoader extends SimpleJavaFileObject {
        URL url;

        SourceInClassLoader(String path) {
            super(getName(path), Kind.SOURCE);
            url = getClass().getClassLoader().getResource(path);
        }

        private static URI getName(String path) {
            return URI.create(path.substring(path.lastIndexOf('/') + 1));
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            StringBuilder bld = new StringBuilder();

            Reader rdr = openReader(ignoreEncodingErrors);
            char[] buffer = new char[512]; //our source files are small

            for (int cnt; (cnt = rdr.read(buffer)) != -1; ) {
                bld.append(buffer, 0, cnt);
            }

            rdr.close();

            return bld;
        }

        @Override
        public NestingKind getNestingKind() {
            return NestingKind.TOP_LEVEL;
        }

        @Override
        public InputStream openInputStream() throws IOException {
            return url.openStream();
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            return new InputStreamReader(openInputStream(), "UTF-8");
        }
    }

    private static class MarkerAnnotationObject extends SimpleJavaFileObject {
        public static final String CLASS_NAME = "__RevapiMarkerAnnotation";

        private static final String SOURCE = "public @interface " + CLASS_NAME + " {}";

        public MarkerAnnotationObject() throws URISyntaxException {
            super(new URI(CLASS_NAME + ".java"), Kind.SOURCE);
        }

        @Override
        public NestingKind getNestingKind() {
            return NestingKind.TOP_LEVEL;
        }

        @Override
        public InputStream openInputStream() throws IOException {
            return new ByteArrayInputStream(SOURCE.getBytes());
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            return new StringReader(SOURCE);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return SOURCE;
        }
    }

    private static class ArchiveProbeObject extends SimpleJavaFileObject {
        public static final String CLASS_NAME = "Probe";

        private String source;

        public ArchiveProbeObject() {
            super(getSourceFileName(), Kind.SOURCE);
        }

        private static URI getSourceFileName() {
            try {
                return new URI(CLASS_NAME + ".java");
            } catch (URISyntaxException e) {
                //doesn't happen
                return null;
            }
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            generateIfNeeded();
            return source;
        }

        @Override
        public NestingKind getNestingKind() {
            return NestingKind.TOP_LEVEL;
        }

        @Override
        public InputStream openInputStream() throws IOException {
            generateIfNeeded();
            return new ByteArrayInputStream(source.getBytes());
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            generateIfNeeded();
            return new StringReader(source);
        }

        private void generateIfNeeded() throws IOException {
            if (source != null) {
                return;
            }

            //notice that we don't actually need to generate any complicated code. Having the classes on the classpath
            //is enough for them to be present in the model captured during the annotation processing.
            source = "@" + MarkerAnnotationObject.CLASS_NAME + "\npublic class " + CLASS_NAME + "\n{}\n";
        }
    }
}
