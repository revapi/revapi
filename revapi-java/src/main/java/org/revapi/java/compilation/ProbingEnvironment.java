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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.annotation.Nonnull;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.revapi.API;
import org.revapi.Archive;
import org.revapi.java.model.JavaElementFactory;
import org.revapi.java.model.JavaElementForest;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.TypeEnvironment;

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
    private Map<TypeElement, org.revapi.java.model.TypeElement> typeMap;
    private Map<TypeElement, Set<TypeElement>> derivedTypes = new HashMap<>();
    private Map<TypeElement, Set<TypeElement>> superTypes = new HashMap<>();

    public ProbingEnvironment(API api) {
        this.api = api;
        this.tree = new JavaElementForest(api, this);
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

    public boolean hasProcessingEnvironment() {
        return processingEnvironment != null;
    }

    public boolean isScanningComplete() {
        return typeMap != null;
    }

    @Nonnull
    @Override
    public Elements getElementUtils() {
        if (processingEnvironment == null) {
            throw new IllegalStateException("Types instance not yet available. It is too early to call this method." +
                    " Wait until after the archives are visited and the API model constructed.");
        }
        return new MissingTypeAwareDelegatingElements(processingEnvironment.getElementUtils());
    }

    @Nonnull
    @Override
    @SuppressWarnings("ConstantConditions")
    public Types getTypeUtils() {
        if (processingEnvironment == null) {
            throw new IllegalStateException("Types instance not yet available. It is too early to call this method." +
                    " Wait until after the archives are visited and the API model constructed.");
        }
        return new MissingTypeAwareDelegatingTypes(processingEnvironment.getTypeUtils());
    }

    void setTypeMap(Map<TypeElement, org.revapi.java.model.TypeElement> typeMap) {
        this.typeMap = Collections.unmodifiableMap(typeMap);
    }

    public Map<TypeElement, org.revapi.java.model.TypeElement> getTypeMap() {
        return typeMap;
    }

    @Override
    public JavaTypeElement getModelElement(TypeElement javaType) {
        JavaTypeElement ret = typeMap == null ? null : typeMap.get(javaType);

        if (ret != null) {
            return ret;
        }

        return (JavaTypeElement) JavaElementFactory.elementFor(javaType, javaType.asType(), this, new Archive() {
            @Nonnull
            @Override
            public String getName() {
                return "<unknown>";
            }

            @Nonnull
            @Override
            public InputStream openStream() throws IOException {
                throw new IOException("Not supported.");
            }
        });
    }

    public Set<TypeElement> getDerivedTypes(TypeElement superType) {
        return derivedTypes.getOrDefault(superType, Collections.emptySet());
    }

    void setSuperTypes(TypeElement derivedType, Collection<TypeElement> superTypes) {
        this.superTypes.computeIfAbsent(derivedType, x -> new HashSet<>(superTypes));
        superTypes.forEach(t -> derivedTypes.computeIfAbsent(t, x -> new HashSet<>()).add(derivedType));

        for (TypeElement superType : superTypes) {
            Set<TypeElement> grandTypes = this.superTypes.get(superType);
            if (grandTypes != null) {
                setSuperTypes(derivedType, grandTypes);
            }
        }
    }
}
