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
package org.revapi.java.filters;

import static java.util.stream.Collectors.toList;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.ElementFilter;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukas Krejci
 * @since 0.7.0
 * @deprecated use the generic revapi filter + java matchers to achieve the same as is done here
 */
@Deprecated
abstract class AbstractIncludeExcludeFilter implements ElementFilter {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractIncludeExcludeFilter.class);

    private final String configurationRootPath;
    private final String schemaPath;
    private final IdentityHashMap<Object, InclusionState> elementResults = new IdentityHashMap<>();
    protected Predicate<String> includeTest;
    protected Predicate<String> excludeTest;
    protected boolean doNothing;

    protected AbstractIncludeExcludeFilter(String configurationRootPath, String schemaPath) {
        this.configurationRootPath = configurationRootPath;
        this.schemaPath = schemaPath;
    }

    protected Predicate<String> composeTest(List<String> fullMatches, List<Pattern> patterns) {
        if (fullMatches != null && fullMatches.size() > 0) {
            return s -> Collections.binarySearch(fullMatches, s) >= 0;
        } else if (patterns != null && patterns.size() > 0) {
            return s -> patterns.stream().anyMatch(p -> p.matcher(s).matches());
        } else {
            return null;
        }
    }

    @Override
    public void close() throws Exception {
        elementResults.clear();
    }

    @Override
    public @Nullable String getExtensionId() {
        return configurationRootPath;
    }

    @Override
    @Nullable
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream(schemaPath), StandardCharsets.UTF_8);
    }


    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        JsonNode root = analysisContext.getConfigurationNode();
        if (root.isNull()) {
            doNothing = true;
            LOG.warn("Filtering using the revapi.java.filter.annotated has been deprecated in favor of revapi.filter" +
                    " together with the java specific matchers (matcher.java).");
            return;
        }

        JsonNode regex = root.path("regex");
        boolean regexes = regex.asBoolean(false);

        List<String> fullMatches = new ArrayList<>();
        List<Pattern> patterns = new ArrayList<>();

        readMatches(root.path("exclude"), regexes, fullMatches, patterns);

        validateConfiguration(true, fullMatches, patterns, regexes);

        this.excludeTest = composeTest(fullMatches, patterns);

        fullMatches = new ArrayList<>();
        patterns = new ArrayList<>();

        readMatches(root.path("include"), regexes, fullMatches, patterns);

        validateConfiguration(false, fullMatches, patterns, regexes);

        this.includeTest = composeTest(fullMatches, patterns);

        doNothing = includeTest == null && excludeTest == null;

        if (!doNothing) {
            LOG.warn("Filtering using the revapi.java.filter.annotated has been deprecated in favor of revapi.filter" +
                    " together with the java specific matchers (matcher.java).");
        }
    }

    protected abstract void validateConfiguration(boolean excludes, List<String> fullMatches, List<Pattern> patterns,
            boolean regexes);

    private void readMatches(JsonNode array, boolean regexes, List<String> fullMatches, List<Pattern> patterns) {
        if (!array.isArray()) {
            return;
        }

        for (JsonNode ann : array) {
            String name = ann.asText();

            if (regexes) {
                patterns.add(Pattern.compile(name));
            } else {
                fullMatches.add(name);
            }
        }

        if (!regexes) {
            Collections.sort(fullMatches);
        }
    }


    @Override
    public boolean applies(@Nullable Element<?> element) {
        return decide(element);
    }

    @Override
    public boolean shouldDescendInto(@Nullable Object element) {
        return true;
    }

    @SuppressWarnings("ConstantConditions")
    private boolean decide(@Nullable Object element) {
        //we don't exclude anything that we don't handle...
        if (doNothing || !(element instanceof JavaElement)) {
            return true;
        }

        InclusionState ret = elementResults.get(element);
        if (ret != null) {
            return ret.toBoolean();
        }

        JavaElement el = (JavaElement) element;

        //exploit the fact that parent elements are always filtered before the children
        Element parent = el.getParent();
        InclusionState parentInclusionState = parent == null ? InclusionState.UNDECIDED
                : elementResults.get(parent);

        //if we have no record of the parent inclusion, then this is a top-level class. Assume it wants to be included.
        if (parentInclusionState == null) {
            parentInclusionState = InclusionState.UNDECIDED;
        }

        //this is a java element, but not a model-based element - i.e. this is an annotation.
        if (!(element instanceof JavaModelElement)) {
            return decideAnnotation((JavaAnnotationElement) element, parentInclusionState);
        }

        JavaModelElement javaElement = (JavaModelElement) element;

        Stream<String> tested = getTestedElementRepresentations(javaElement);

        //let's first assume we're going to inherit the parent's inclusion state
        ret = parentInclusionState;

        //now see if we need to change that assumption
        switch (parentInclusionState) {
        case INCLUDED:
            //the parent was explicitly included in the results. We therefore only need to check if the annotations
            //on this element should be excluded
            if (excludeTest != null) {
                if (tested.anyMatch(s -> excludeTest.test(s))) {
                    ret = InclusionState.EXCLUDED;
                }
            }
            break;
        case EXCLUDED:
            if (!canBeReIncluded(javaElement)) {
                break;
            }
            //the child element can be re-included, so the full suite of tests need to be run.
            //i.e. this fall-through is intentional.
        case UNDECIDED:
            //ok, the parent is undecided. This means we have to do the full checks on this element.
            List<String> testedList = null;
            if (includeTest != null && excludeTest != null) {
                testedList = tested.collect(toList());
                tested = testedList.stream();
            }

            if (includeTest != null) {
                //ok, there is an include test but the parent is undecided. This means that the parent actually
                //didn't match the include test. Let's check with this element.
                ret = tested.anyMatch(s -> includeTest.test(s))
                        ? InclusionState.INCLUDED
                        : InclusionState.EXCLUDED;
            }

            if (excludeTest != null) {
                if (testedList != null) {
                    tested = testedList.stream();
                }

                //there is an exclude test but the parent is undecided. This means that the exclude check didn't
                //match the parent. Let's check again with this element.

                if (tested.anyMatch(s -> excludeTest.test(s))) {
                    ret = InclusionState.EXCLUDED;
                }
            }
            break;
        }

        elementResults.put(element, ret);
        return ret.toBoolean();
    }

    boolean decideAnnotation(JavaAnnotationElement annotation, InclusionState parentInclusionState) {
        //annotations cannot be annotated but it would also be awkward to check a method and NOT its annotations...
        //therefore we just include the annotations based on the inclusion state of the annotated element.
        return parentInclusionState.toBoolean();
    }

    protected abstract boolean canBeReIncluded(JavaModelElement element);

    protected abstract Stream<String> getTestedElementRepresentations(JavaModelElement element);

    protected enum InclusionState {
        /**
         * The element was explicitly determined to be included
         */
        INCLUDED,

        /**
         * The element was explicitly determined to be excluded
         */
        EXCLUDED,

        /**
         * There was no precise decision possible on the element
         */
        UNDECIDED;


        boolean toBoolean() {
            switch (this) {
            case INCLUDED:
            case UNDECIDED:
                return true;
            default:
                return false;
            }
        }
    }
}
