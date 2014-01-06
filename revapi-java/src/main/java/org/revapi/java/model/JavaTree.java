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

package org.revapi.java.model;

import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.revapi.Element;
import org.revapi.query.Filter;
import org.revapi.simple.SimpleTree;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class JavaTree extends SimpleTree {

    private Future<?> compilation;

    public JavaTree() {
    }

    public void setCompilationFuture(Future<?> compilation) {
        this.compilation = compilation;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SortedSet<TypeElement> getRoots() {
        waitForCompilation();
        return (SortedSet<TypeElement>) super.getRoots();
    }

    @SuppressWarnings("unchecked")
    public SortedSet<TypeElement> getRootsUnsafe() {
        return (SortedSet<TypeElement>) super.getRoots();
    }

    @Override
    public <T extends Element> void search(List<T> results, Class<T> resultType,
        SortedSet<? extends Element> currentLevel, boolean recurse, Filter<? super T> filter) {
        waitForCompilation();
        super.search(results, resultType, currentLevel, recurse, filter);
    }

    @Override
    public <T extends Element> List<T> search(Class<T> resultType, boolean recurse, Filter<? super T> filter,
        Element root) {
        waitForCompilation();
        return super.search(resultType, recurse, filter, root);
    }

    private void waitForCompilation() {
        try {
            if (compilation != null) {
                compilation.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for compilation to finish.");
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to obtain class tree due to compilation failure.", e.getCause());
        }
    }
}
