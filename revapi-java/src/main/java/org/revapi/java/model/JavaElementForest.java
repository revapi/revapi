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
package org.revapi.java.model;

import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.API;
import org.revapi.Element;
import org.revapi.java.Timing;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.query.Filter;
import org.revapi.simple.SimpleElement;
import org.revapi.simple.SimpleElementForest;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class JavaElementForest extends SimpleElementForest {

    private Future<?> compilation;
    private final ProbingEnvironment environment;
    private static final ThreadLocal<Boolean> UNSAFE_MODE = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    public JavaElementForest(API api, ProbingEnvironment environment) {
        super(api);
        this.environment = environment;
    }

    public void setCompilationFuture(Future<?> compilation) {
        this.compilation = compilation;
    }

    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public SortedSet<TypeElement> getRoots() {
        waitForCompilation();
        return (SortedSet<TypeElement>) super.getRoots();
    }

    @SuppressWarnings("unchecked")
    public SortedSet<TypeElement> getRootsUnsafe() {
        boolean wasUnsafe = UNSAFE_MODE.get();
        try {
            UNSAFE_MODE.set(true);
            return (SortedSet<TypeElement>) super.getRoots();
        } finally {
            UNSAFE_MODE.set(wasUnsafe);
        }
    }

    @Override
    public <T extends Element> void search(@Nonnull List<T> results, @Nonnull Class<T> resultType,
        @Nonnull SortedSet<? extends Element> currentLevel, boolean recurse, @Nullable Filter<? super T> filter) {
        waitForCompilation();
        super.search(results, resultType, currentLevel, recurse, filter);
    }

    @Nonnull
    @Override
    public <T extends Element> List<T> search(@Nonnull Class<T> resultType, boolean recurse,
        @Nullable Filter<? super T> filter,
        @Nullable Element root) {
        waitForCompilation();
        return super.search(resultType, recurse, filter, root);
    }

    public <T extends Element> List<T> searchUnsafe(Class<T> resultType, boolean recurse, Filter<? super T> filter,
        Element root) {
        boolean wasUnsafe = UNSAFE_MODE.get();
        try {
            UNSAFE_MODE.set(true);
            return super.search(resultType, recurse, filter, root);
        } finally {
            UNSAFE_MODE.set(wasUnsafe);
        }
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

    @Override
    protected SortedSet<? extends SimpleElement> newRootsInstance() {
        return new UseSiteUpdatingSortedSet<>(environment, super.newRootsInstance());
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
