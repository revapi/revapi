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
package org.revapi.java.test.support;

import static java.util.Collections.singletonList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * This class can be used in tests to make it easy to compile custom source code into jars and then use the Java
 * Annotation processing API to analyze the compiled classes.
 *
 * <p>Simply declare a JUnit rule field:<pre><code>
 *    {@literal @Rule}
 *     public Jar jar = new Jar();
 * </code></pre>
 *
 * <p>and then use it in your test methods to compile and use code:<pre><code>
 *     Jar.BuildOutput build = jar.from()
 *         .classPathSources("/", "my/package/MySource.java")
 *         .classPathResources("/", "META-INF/my-file-in-jar.txt")
 *         .build();
 *
 *     Jar.Environment env = build.analyze();
 *     TypeElement mySourceClass = env.elements().getElement("my.package.MySource");
 *     ...
 *     File jarFile = build.jarFile();
 *     Files.copy(jarFile.toPath(), Paths.get("/"));
 *     ...
 * </code></pre>
 *
 * If you use this class as a JUnit rule, you don't have to handle cleanup of the compilation results. It will be done
 * automagically.
 *
 * @author Lukas Krejci
 * @since 0.1.0
 */
public class Jar implements TestRule {
    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    private Map<File, Semaphore> compiledStuff = new HashMap<>();

    private ExecutorService compileProcess = Executors.newCachedThreadPool();

    /**
     * Applies a jar rule to a test method. Don't call directly but instead let JUnit handle it.
     */
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

    /**
     * Instantiates a builder using which the contents of a compiled jar file can be composed.
     * @return a builder to gather sources and resources to compile and compose a jar file
     */
    public Builder from() {
        return new Builder();
    }

    public final class Builder {
        private Map<URI, JavaFileObject> sources = new HashMap<>();
        private Map<URI, InputStream> resources = new HashMap<>();

        private Builder() {
        }

        /**
         * Finds given sources under given root in the classpath. The resulting jar file will contain the compiled
         * classes on the same relatives paths as the provided sources.
         *
         * @param root the root path in the classloader to resolve the sources against
         * @param sources the list of relative paths on which the source files are located in the classloader
         * @return this instance
         */
        public Builder classPathSources(String root, String... sources) {
            URI rootUri = toUri(root);

            for (String source : sources) {
                URI sourceUri = URI.create(source);
                URI location = rootUri.resolve(sourceUri);

                this.sources.put(sourceUri, new SourceInClassLoader(sourceUri, location));
            }

            return this;
        }

        /**
         * Adds given resources to the compiled jar file. The paths to the resources are resolved in the same way
         * as with sources.
         *
         * @param root the root against which to resolve the resource paths in the classloader
         * @param resources the relative paths of the resources
         * @return this instance
         *
         * @see #classPathSources(String, String...)
         */
        public Builder classPathResources(String root, String... resources) {
            URI rootUri = toUri(root);

            for (String resource : resources) {
                URI resourceUri = URI.create(resource);
                URI location = rootUri.resolve(resourceUri);

                this.resources.put(resourceUri, getClass().getResourceAsStream(location.getPath()));
            }

            return this;
        }

        /**
         * Similar to {@link #classPathSources(String, String...)} but locates the sources to compile using actual
         * files.
         */
        public Builder fileSources(File root, File... sources) {
            URI rootUri = root.toURI();

            for (File source : sources) {
                URI sourceUri = URI.create(source.getPath());
                URI location = rootUri.resolve(sourceUri);

                this.sources.put(sourceUri, new FileJavaFileObject(sourceUri, new File(location.getPath())));
            }

            return this;
        }

        /**
         * Similar to {@link #classPathResources(String, String...)} but locates the sources to compile using actual
         * files.
         */
        public Builder fileResources(File root, File... resources) {
            URI rootUri = root.toURI();

            for (File resource : resources) {
                URI resourceUri = URI.create(resource.getPath());
                URI location = rootUri.resolve(resourceUri);

                try {
                    this.resources.put(resourceUri, new FileInputStream(location.getPath()));
                } catch (FileNotFoundException e) {
                    throw new IllegalArgumentException(e);
                }
            }

            return this;
        }

        /**
         * Compiles the sources and composes a jar file that comprises of the class files on the specified locations
         * (defined by {@link #classPathSources(String, String...)} et al.) along with some resources on the specified
         * locations (defined by {@link #classPathResources(String, String...)} et al.).
         *
         * @return an object to access the results of the compilation
         * @throws IOException on error
         */
        public BuildOutput build() throws IOException {
            File dir = Files.createTempDirectory("revapi-java-spi").toFile();

            File compiledSourcesOutput = new File(dir, "sources");
            if (!compiledSourcesOutput.mkdirs()) {
                throw new IllegalStateException("Could not create output location for compiling test sources.");
            }

            List<JavaFileObject> sourceObjects = new ArrayList<>(sources.values());

            List<String> options = Arrays.asList("-d", compiledSourcesOutput.getAbsolutePath());

            JavaCompiler.CompilationTask firstCompilation = compiler.getTask(null, null, null, options, null, sourceObjects);
            if (!firstCompilation.call()) {
                throw new IllegalStateException("Failed to compile the sources");
            }

            for (Map.Entry<URI, InputStream> e : resources.entrySet()) {
                File target = new File(compiledSourcesOutput, e.getKey().getPath());
                if (!target.getParentFile().mkdirs()) {
                    throw new IllegalStateException("Failed to create directory " + target.getParentFile().getAbsolutePath());
                }
                Files.copy(e.getValue(), target.toPath());
            }

            File compiledJar = new File(dir, "compiled.jar");
            try (JarOutputStream out = new JarOutputStream(new FileOutputStream(compiledJar))) {
                Path root = compiledSourcesOutput.toPath();
                Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        ZipEntry entry = new ZipEntry(root.relativize(file).toFile().getPath());
                        out.putNextEntry(entry);
                        Files.copy(file, out);
                        out.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            }

            compiledStuff.put(dir, null);

            return new BuildOutput(compiledJar);
        }

        private URI toUri(String path) {
            if (path == null || path.isEmpty()) {
                return URI.create("/");
            } else {
                return URI.create(path);
            }
        }
    }

    /**
     * Gives access to the compilation results.
     */
    public final class BuildOutput {
        private final File jarFile;

        public BuildOutput(File jarFile) {
            this.jarFile = jarFile;
        }

        /**
         * @return the compiled jar file
         */
        public File jarFile() {
            return jarFile;
        }

        /**
         * @return an environment similar to java annotation processing round environment that gives access to
         * {@link Elements} and {@link Types} instances that can be used to analyze the compiled classes.
         */
        public Environment analyze() {
            File dir = new File(jarFile.getParent(), "probe");
            if (!dir.mkdirs()) {
                throw new IllegalArgumentException("Failed to create directory " + dir.getAbsolutePath());
            }

            List<String> options = Arrays.asList("-cp", jarFile.getAbsolutePath(),
                    "-d", dir.getAbsolutePath());

            List<JavaFileObject> sourceObjects = new ArrayList<>(2);
            sourceObjects.add(new MarkerAnnotationObject());
            sourceObjects.add(new ArchiveProbeObject());

            StandardJavaFileManager fileManager = compiler
                    .getStandardFileManager(null, Locale.getDefault(), Charset.forName("UTF-8"));

            JavaCompiler.CompilationTask task = compiler
                    .getTask(new PrintWriter(System.out), fileManager, null, options, singletonList(ArchiveProbeObject.CLASS_NAME), sourceObjects);


            final Semaphore cleanUpSemaphore = new Semaphore(0);
            final Semaphore initSemaphore = new Semaphore(0);

            final EnvironmentImpl ret = new EnvironmentImpl();

            task.setProcessors(singletonList(new AbstractProcessor() {
                @Override
                public SourceVersion getSupportedSourceVersion() {
                    return SourceVersion.latest();
                }

                @Override
                public Set<String> getSupportedAnnotationTypes() {
                    return new HashSet<>(singletonList(MarkerAnnotationObject.CLASS_NAME));
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

            compileProcess.submit(task);

            try {
                initSemaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Thread interrupted");
            }

            compiledStuff.put(jarFile.getParentFile(), cleanUpSemaphore);

            return ret;
        }
    }

    /**
     * If you're using the Jar instance as a JUnit rule, you don't have to call this method. Otherwise this can be used
     * to remove the compiled jar files from the filesystem.
     *
     * @throws IOException on error
     */
    @SuppressWarnings({"ResultOfMethodCallIgnored", "ConstantConditions"})
    public void cleanUp() throws IOException {
        for (Map.Entry<File, Semaphore> e : compiledStuff.entrySet()) {
            if (e.getValue() != null) {
                e.getValue().release();
            }

            Files.walkFileTree(e.getKey().toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public interface Environment {
        Elements elements();

        Types types();
    }

    private static final class EnvironmentImpl implements Environment {
        private Elements elements;
        private Types types;

        @Override
        public Elements elements() {
            return elements;
        }

        @Override
        public Types types() {
            return types;
        }
    }
}
