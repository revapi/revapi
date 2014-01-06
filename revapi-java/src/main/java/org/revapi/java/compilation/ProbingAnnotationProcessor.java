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

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes(MarkerAnnotationObject.CLASS_NAME)
public final class ProbingAnnotationProcessor extends AbstractProcessor {
    private final Object lock = new Object();
    private volatile boolean locked;
    private final ProbingEnvironment environment;

    public ProbingAnnotationProcessor(ProbingEnvironment env) {
        this.environment = env;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            environment.setProcessingEnvironment(processingEnv);
            unlock();
            return true;
        }

        return false;
    }

    public <T> Future<T> waitForProcessingAndExecute(ExecutorService executor, final Callable<T> task)
        throws InterruptedException {

        synchronized (lock) {
            if (locked) {
                lock.wait();
            }

            Future<T> ret = executor.submit(new Callable<T>() {
                @Override
                public T call() throws Exception {
                    T ret = task.call();
                    unlock();
                    return ret;
                }
            });

            lock.wait();
            return ret;
        }
    }

    private void unlock() {
        synchronized (lock) {
            locked = false;
            lock.notifyAll();
        }
    }
}
