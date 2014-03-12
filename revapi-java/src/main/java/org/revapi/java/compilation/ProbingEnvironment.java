/*
 * Copyright 2014 Lukas Krejci
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

package org.revapi.java.compilation;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.annotation.Nonnull;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.revapi.API;
import org.revapi.java.TypeEnvironment;
import org.revapi.java.model.JavaElementForest;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class ProbingEnvironment implements TypeEnvironment {
    private final API api;
    private volatile ProcessingEnvironment processingEnvironment;
    private final CountDownLatch compilationProgressLatch = new CountDownLatch(1);
    private final CountDownLatch compilationEnvironmentTeardownLatch = new CountDownLatch(1);
    private final JavaElementForest tree;
    private final Set<String> forcedApiClasses;

    public ProbingEnvironment(API api) {
        this.api = api;
        this.tree = new JavaElementForest(api);
        this.forcedApiClasses = new HashSet<>();
    }

    public API getApi() {
        return api;
    }

    public CountDownLatch getCompilationTeardownLatch() {
        return compilationEnvironmentTeardownLatch;
    }

    public CountDownLatch getCompilationProgressLatch() {
        return compilationProgressLatch;
    }

    public JavaElementForest getTree() {
        return tree;
    }

    public void setProcessingEnvironment(ProcessingEnvironment env) {
        this.processingEnvironment = env;
    }

    @Nonnull
    @Override
    @SuppressWarnings("ConstantConditions")
    public Elements getElementUtils() {
        return processingEnvironment == null ? null : processingEnvironment.getElementUtils();
    }

    @Nonnull
    @Override
    @SuppressWarnings("ConstantConditions")
    public Types getTypeUtils() {
        return processingEnvironment == null ? null : processingEnvironment.getTypeUtils();
    }

    @Override
    public boolean isExplicitPartOfAPI(@Nonnull TypeElement type) {
        return getAllApiClasses().contains(type.getQualifiedName().toString());
    }

    @Nonnull
    public Set<String> getAllApiClasses() {
        return forcedApiClasses;
    }
}
