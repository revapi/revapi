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
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class CompilationValve {
    private static final Logger LOG = LoggerFactory.getLogger(CompilationValve.class);

    private final Future<Boolean> compilationResult;
    private final File dirToCleanup;
    private final ProbingEnvironment environment;

    /* package private */ CompilationValve(Future<Boolean> results, File dirToCleanup, ProbingEnvironment env) {
        this.compilationResult = results;
        this.dirToCleanup = dirToCleanup;
        this.environment = env;
    }

    ProbingEnvironment getEnvironment() {
        return environment;
    }

    Future<Boolean> getCompilationResult() {
        return compilationResult;
    }

    public void removeCompiledResults() {

        if (LOG.isTraceEnabled()) {
            LOG.trace("Releasing compilation environment for " + environment.getName());
        }
        environment.getCompilationTeardownLatch().countDown();

        if (!compilationResult.isDone()) {
            try {
                compilationResult.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                //but clean up below anyway before returning
            } catch (ExecutionException e) {
                throw new IllegalStateException("Exception thrown while waiting for compilation to end for clean up",
                    e);
            }
        }

        try {
            Files.walkFileTree(dirToCleanup.toPath(), new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    if (dirToCleanup.toPath().equals(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    throw new IOException("Failed to delete file '" + file + "'.", exc);
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to remove compiled results", e);
        }
    }
}
