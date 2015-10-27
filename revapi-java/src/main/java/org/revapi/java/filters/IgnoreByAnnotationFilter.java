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

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.ElementFilter;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.5.1
 */
public final class IgnoreByAnnotationFilter implements ElementFilter {
    private String[] fullMatches;
    private Pattern[] patterns;
    private boolean doNothing;

    private final IdentityHashMap<Element, Boolean> elementResults = new IdentityHashMap<>();

    @Override
    public void close() throws Exception {
        elementResults.clear();
    }

    @Override
    public @Nullable String[] getConfigurationRootPaths() {
        return new String[]{"revapi.java.ignore.annotations"};
    }

    @Override
    public Reader getJSONSchema(@Nonnull String configurationRootPath) {
        if ("revapi.java.ignore.annotations".equals(configurationRootPath)) {
            return new InputStreamReader(getClass().getResourceAsStream("/META-INF/ignore-by-annos-schema.json"),
                    Charset.forName("UTF-8"));
        } else {
            return null;
        }
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        ModelNode root = analysisContext.getConfiguration().get("revapi", "java", "ignore", "annotations");
        if (!root.isDefined()) {
            doNothing = true;
            return;
        }

        ModelNode regex = root.get("regex");
        boolean regexes = regex.isDefined() && regex.asBoolean();

        List<String> fullMatches = regexes ? null : new ArrayList<>();
        List<Pattern> patterns = regexes ? new ArrayList<>() : null;

        ModelNode annotations = root.get("annotations");
        for (ModelNode ann : annotations.asList()) {
            String name = ann.asString();

            if (regexes) {
                patterns.add(Pattern.compile(name));
            } else {
                fullMatches.add(name);
            }
        }

        this.fullMatches = fullMatches == null ? null : fullMatches.toArray(new String[fullMatches.size()]);
        this.patterns = patterns == null ? null : patterns.toArray(new Pattern[patterns.size()]);
        if (fullMatches != null) {
            Collections.sort(fullMatches);
        }

        doNothing = (this.fullMatches != null && this.fullMatches.length == 0)
                || (this.patterns != null && this.patterns.length == 0);
    }

    @Override
    public boolean applies(@Nullable Element element) {
        if (doNothing || !(element instanceof JavaModelElement)) {
            //we don't exclude anything that we don't handle...
            return true;
        }

        Predicate<String> test;
        if (fullMatches == null) {
            test = s -> Stream.of(patterns).anyMatch(p -> p.matcher(s).matches());
        } else {
            test = s -> Arrays.binarySearch(fullMatches, s) >= 0;
        }

        JavaModelElement javaElement = (JavaModelElement) element;

        boolean ret = !javaElement.getModelElement().getAnnotationMirrors().stream().map(Util::toHumanReadableString)
                .anyMatch(test);

        elementResults.put(element, ret);
        return ret;
    }

    @Override
    public boolean shouldDescendInto(@Nullable Object element) {
        if (doNothing || !(element instanceof JavaModelElement)) {
            return true;
        }

        return elementResults.get(element);
    }
}
