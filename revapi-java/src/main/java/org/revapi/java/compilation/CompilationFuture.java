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

import java.io.StringWriter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class CompilationFuture implements Future<Void> {
    private final CompilationValve valve;
    private final StringWriter output;

    public CompilationFuture(CompilationValve valve, StringWriter output) {
        this.valve = valve;
        this.output = output;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return valve.getCompilationResult().isDone() ||
            valve.getEnvironment().getCompilationProgressLatch().getCount() == 0;
    }

    @Override
    public Void get() throws InterruptedException, ExecutionException {
        valve.getEnvironment().getCompilationProgressLatch().await();

        if (valve.getCompilationResult().isDone()) {
            //if at this point the compilation is done, we know there was a problem.
            //The compilation should NOT be done at this moment, because it should be waiting for the releasing of the
            //compilation environment...
            valve.getCompilationResult().get();
            return null;
        }

        if (output.getBuffer().length() > 0) {
            throw new ExecutionException(
                new Exception("Compilation failed while analyzing " + valve.getEnvironment().getApi() + ":\n" +
                    output.toString()));
        }
        return null;
    }

    @Override
    public Void get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {

        if (!valve.getEnvironment().getCompilationProgressLatch().await(timeout, unit)) {
            throw new TimeoutException();
        }

        if (valve.getCompilationResult().isDone()) {
            //if at this point the compilation is done, we know there was a problem.
            //The compilation should NOT be done at this moment, because it should be waiting for the releasing of the
            //compilation environment...
            valve.getCompilationResult().get();
            return null;
        }

        if (output.getBuffer().length() > 0) {
            throw new ExecutionException(
                new Exception("Compilation failed while analyzing " + valve.getEnvironment().getApi() + ":\n" +
                    output.toString()));
        }

        return null;
    }
}
