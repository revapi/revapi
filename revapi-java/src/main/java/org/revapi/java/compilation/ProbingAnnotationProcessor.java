/*
 * Copyright 2014-2023 Lukas Krejci
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

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukas Krejci
 *
 * @since 0.1
 */
final class ProbingAnnotationProcessor extends AbstractProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ProbingAnnotationProcessor.class);

    private final ProbingEnvironment environment;
    private Runnable postCompilationPayload;

    public ProbingAnnotationProcessor(ProbingEnvironment env) {
        this.environment = env;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            environment.setProcessingEnvironment(processingEnv);

            postCompilationPayload.run();

            releaseCompilationProgress();

            try {
                environment.getCompilationTeardownLatch().await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return true;
        }

        return false;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(MarkerAnnotationObject.CLASS_NAME);
    }

    public <T> Future<T> submitWithCompilationAwareness(ExecutorService executor, final Callable<T> compilation,
            final Runnable postCompilePayload) throws Exception {

        return executor.submit(new Callable<T>() {
            @Override
            public T call() throws Exception {
                try {
                    ProbingAnnotationProcessor.this.postCompilationPayload = postCompilePayload;
                    return compilation.call();
                } finally {
                    releaseCompilationProgress();
                }
            }
        });
    }

    private void releaseCompilationProgress() {
        if (LOG.isTraceEnabled() && environment.getCompilationProgressLatch().getCount() > 0) {
            LOG.trace("Releasing compilation progress for " + environment.getApi());
        }
        environment.getCompilationProgressLatch().countDown();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}
