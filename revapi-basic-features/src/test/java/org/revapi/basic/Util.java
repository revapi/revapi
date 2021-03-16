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
/*
yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy * Copyright 2014-2020 Lukas Krejci
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
package org.revapi.basic;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

import java.util.Optional;
import java.util.Set;

import org.junit.Assert;
import org.revapi.AnalysisContext;
import org.revapi.ApiAnalyzer;
import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.Element;
import org.revapi.ElementMatcher;
import org.revapi.PipelineConfiguration;
import org.revapi.Reporter;
import org.revapi.Revapi;
import org.revapi.TransformationResult;
import org.revapi.TreeFilterProvider;

/**
 * @author Lukas Krejci
 * 
 * @since 0.6.0
 */
final class Util {

    private Util() {

    }

    static AnalysisContext getAnalysisContextFromFullConfig(Class<?> extensionType, String fullConfig) {
        AnalysisContext fullCtx = AnalysisContext.builder().withConfigurationFromJSON(fullConfig).build();
        return dummyRevapi(extensionType).prepareAnalysis(fullCtx).getFirstConfigurationOrNull(extensionType);
    }

    static AnalysisContext setAnalysisContextFullConfig(AnalysisContext.Builder bld, Class<?> extensionType,
            String fullConfig) {
        AnalysisContext fullCtx = bld.withConfigurationFromJSON(fullConfig).build();
        return dummyRevapi(extensionType).prepareAnalysis(fullCtx).getFirstConfigurationOrNull(extensionType);
    }

    static <T extends Element<T>> Difference transformAndAssumeOne(DifferenceTransform<T> transform, T oldEl, T newEl,
            Difference orig) {
        Optional<DifferenceTransform.TraversalTracker<T>> otr = transform.startTraversal(null, null, null);
        otr.ifPresent(tr -> {
            tr.startElements(oldEl, newEl);
            tr.endElements(oldEl, newEl);
            tr.endTraversal();
        });

        try {
            TransformationResult res = transform.tryTransform(oldEl, newEl, orig);
            switch (res.getResolution()) {
            case KEEP:
                return orig;
            case DISCARD:
                return null;
            case REPLACE:
                Assert.assertNotNull(res.getDifferences());
                Assert.assertEquals(1, res.getDifferences().size());
                return res.getDifferences().iterator().next();
            case UNDECIDED:
                Assert.fail("Unexpected undecided transform.");
            default:
                throw new AssertionError("Unhandled resolution type: " + res.getResolution());
            }
        } finally {
            transform.endTraversal(otr.orElse(null));
        }
    }

    private static Revapi dummyRevapi(Class<?> extensionType) {
        Set<Class<? extends ApiAnalyzer>> analyzers = setOrEmpty(ApiAnalyzer.class, extensionType);
        Set<Class<? extends TreeFilterProvider>> filters = setOrEmpty(TreeFilterProvider.class, extensionType);
        Set<Class<? extends DifferenceTransform>> transforms = setOrEmpty(DifferenceTransform.class, extensionType);
        Set<Class<? extends Reporter>> reporters = setOrEmpty(Reporter.class, extensionType);
        Set<Class<? extends ElementMatcher>> matchers = setOrEmpty(ElementMatcher.class, extensionType);

        return new Revapi(PipelineConfiguration.builder().withAnalyzers(analyzers).withReporters(reporters)
                .withTransforms(transforms).withFilters(filters).withMatchers(matchers).build());
    }

    @SuppressWarnings("unchecked")
    private static <T> Set<Class<? extends T>> setOrEmpty(Class<T> expectedType, Class<?> actualType) {
        if (expectedType.isAssignableFrom(actualType)) {
            return singleton((Class<? extends T>) actualType);
        } else {
            return emptySet();
        }
    }
}
