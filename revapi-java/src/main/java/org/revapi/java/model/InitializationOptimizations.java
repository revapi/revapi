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

import org.revapi.base.BaseElement;
import org.revapi.java.spi.JavaElement;

/**
 * This class contains an assortment of methods that enable various optimizations and speedups during the initial
 * production of the java element forest without needing to expose the implementation details of the various element
 * types implemented in this package.
 */
public final class InitializationOptimizations {

    private InitializationOptimizations() {

    }

    public static void initializeComparator(JavaElement el) {
        if (el instanceof JavaElementBase) {
            ((JavaElementBase<?, ?>) el).getComparableSignature();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends JavaElement> T clone(T orig) {
        if (orig instanceof BaseElement) {
            return (T) ((BaseElement) orig).clone();
        }

        return orig;
    }

    public static String getMethodComparisonKey(MethodElement method) {
        return method.getComparableSignature();
    }
}
