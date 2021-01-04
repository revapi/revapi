/*
 * Copyright 2014-2021 Lukas Krejci
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
package org.revapi.java.model;

import java.util.Iterator;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.API;
import org.revapi.Element;
import org.revapi.base.BaseElementForest;
import org.revapi.java.Timing;
import org.revapi.java.spi.JavaElement;
import org.revapi.query.Filter;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class JavaElementForest extends BaseElementForest<JavaElement> {

    private Future<?> compilation;
    private static final ThreadLocal<Boolean> UNSAFE_MODE = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    public JavaElementForest(API api) {
        super(api);
    }

    public void setCompilationFuture(Future<?> compilation) {
        this.compilation = compilation;
    }

    @Override
    public SortedSet<JavaElement> getRoots() {
        waitForCompilation();
        return super.getRoots();
    }

    public SortedSet<JavaElement> getRootsUnsafe() {
        boolean wasUnsafe = UNSAFE_MODE.get();
        try {
            UNSAFE_MODE.set(true);
            return super.getRoots();
        } finally {
            UNSAFE_MODE.set(wasUnsafe);
        }
    }

    @Nonnull
    @Override
    public <T extends Element<T>> Iterator<T> iterateOverElements(@Nonnull Class<T> resultType, boolean recurse,
            @Nullable Filter<? super T> filter, @Nullable Element<T> searchRoot) {
        waitForCompilation();
        return super.iterateOverElements(resultType, recurse, filter, searchRoot);
    }

    @Nonnull
    @Override
    public <T extends Element<JavaElement>> Stream<T> stream(@Nonnull Class<T> resultType, boolean recurse,
            @Nullable Element<JavaElement> searchRoot) {
        waitForCompilation();
        return super.stream(resultType, recurse, searchRoot);
    }

    @Override
    public String toString() {
        boolean unsafe = UNSAFE_MODE.get();
        try {
            UNSAFE_MODE.set(true);
            return super.toString();
        } finally {
            UNSAFE_MODE.set(unsafe);
        }
    }

    private void waitForCompilation() {
        try {
            if (compilation != null && !UNSAFE_MODE.get()) {
                compilation.get();
                compilation = null;
                if (Timing.LOG.isDebugEnabled()) {
                    Timing.LOG.debug("Compilation completed for " + getApi());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for compilation to finish.");
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to obtain class tree due to compilation failure.", e.getCause());
        }
    }
}
