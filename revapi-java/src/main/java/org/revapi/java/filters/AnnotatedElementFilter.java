/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;

import org.jboss.dmr.ModelNode;
import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.ElementFilter;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.5.1
 */
public final class AnnotatedElementFilter implements ElementFilter {
    private static final String CONFIG_ROOT_PATH = "revapi.java.filter.annotated";
    private final IdentityHashMap<Object, InclusionState> elementResults = new IdentityHashMap<>();
    private Predicate<String> includeTest;
    private Predicate<String> excludeTest;
    private boolean doNothing;

    private static Predicate<String> composeTest(List<String> fullMatches, List<Pattern> patterns) {
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
    public @Nullable String[] getConfigurationRootPaths() {
        return new String[]{CONFIG_ROOT_PATH};
    }

    @Override
    public Reader getJSONSchema(@Nonnull String configurationRootPath) {
        if (CONFIG_ROOT_PATH.equals(configurationRootPath)) {
            return new InputStreamReader(getClass().getResourceAsStream("/META-INF/annotated-elem-filter-schema.json"),
                    Charset.forName("UTF-8"));
        } else {
            return null;
        }
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        ModelNode root = analysisContext.getConfiguration().get("revapi", "java", "filter", "annotated");
        if (!root.isDefined()) {
            doNothing = true;
            return;
        }

        ModelNode regex = root.get("regex");
        boolean regexes = regex.isDefined() && regex.asBoolean();

        List<String> fullMatches = new ArrayList<>();
        List<Pattern> patterns = new ArrayList<>();

        readAnnotations(root.get("exclude"), regexes, fullMatches, patterns);

        this.excludeTest = composeTest(fullMatches, patterns);

        fullMatches = new ArrayList<>();
        patterns = new ArrayList<>();

        readAnnotations(root.get("include"), regexes, fullMatches, patterns);

        this.includeTest = composeTest(fullMatches, patterns);

        doNothing = includeTest == null && excludeTest == null;
    }

    @Override
    public boolean applies(@Nullable Element element) {
        return decide(element);
    }

    @Override
    public boolean shouldDescendInto(@Nullable Object element) {
        return doNothing || (element instanceof JavaTypeElement || element instanceof JavaMethodElement);
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

        //this is a java element, but not a model-based element - i.e. this is most probably an annotation. Annotations
        //can never be annotated (as opposed to annotation types), so include this if there are no explicit inclusion
        //tests (which can never be satisfied) and the parent is included.
        if (!(element instanceof JavaModelElement)) {
            return parentInclusionState.toBoolean() && includeTest == null;
        }

        JavaModelElement javaElement = (JavaModelElement) element;

        List<? extends AnnotationMirror> annos = javaElement.getModelElement().getAnnotationMirrors();

        //let's first assume we're going to inherit the parent's inclusion state
        ret = parentInclusionState;

        //now see if we need to change that assumption
        switch (parentInclusionState) {
            case INCLUDED:
                //the parent was explicitly included in the results. We therefore only need to check if the annotations
                //on this element should be excluded
                if (excludeTest != null) {
                    if (annos.stream().map(Util::toHumanReadableString).anyMatch(s -> excludeTest.test(s))) {
                        ret = InclusionState.EXCLUDED;
                    }
                }
                break;
            case EXCLUDED:
                //the child element can be re-included, so the full suite of tests need to be run.
                //i.e. this fall-through is intentional.
            case UNDECIDED:
                //ok, the parent is undecided. This means we have to do the full checks on this element.
                List<String> stringAnnos = null;
                if (includeTest != null || excludeTest != null) {
                    stringAnnos = annos.stream().map(Util::toHumanReadableString).collect(toList());
                }

                if (includeTest != null) {
                    //ok, there is an include test but the parent is undecided. This means that the parent actually
                    //didn't match the include test. Let's check with this element.

                    //only bother with this if there are some annos to check
                    if (!annos.isEmpty()) {
                        if (stringAnnos.stream().anyMatch(s -> includeTest.test(s))) {
                            ret = InclusionState.INCLUDED;
                        } else {
                            ret = InclusionState.EXCLUDED;
                        }
                    } else {
                        ret = InclusionState.EXCLUDED;
                    }
                }

                if (excludeTest != null) {
                    //there is an exclude test but the parent is undecided. This means that the exclude check didn't
                    //match the parent. Let's check again with this element.

                    if (stringAnnos.stream().anyMatch(s -> excludeTest.test(s))) {
                        ret = InclusionState.EXCLUDED;
                    }
                }
                break;
        }

        elementResults.put(element, ret);
        return ret.toBoolean();
    }

    private void readAnnotations(ModelNode array, boolean regexes, List<String> fullMatches, List<Pattern> patterns) {
        if (!array.isDefined()) {
            return;
        }

        for (ModelNode ann : array.asList()) {
            String name = ann.asString();

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

    private enum InclusionState {
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
