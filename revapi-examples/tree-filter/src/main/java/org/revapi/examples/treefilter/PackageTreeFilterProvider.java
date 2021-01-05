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
package org.revapi.examples.treefilter;

import java.io.Reader;
import java.io.StringReader;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.lang.model.element.PackageElement;

import org.revapi.AnalysisContext;
import org.revapi.ArchiveAnalyzer;
import org.revapi.Element;
import org.revapi.FilterMatch;
import org.revapi.FilterStartResult;
import org.revapi.TreeFilter;
import org.revapi.TreeFilterProvider;
import org.revapi.base.IndependentTreeFilter;
import org.revapi.java.spi.JavaTypeElement;

/**
 * Tree filters are used to leave out certain parts of the API trees from the analysis. The actual interface that
 * the extensions need to implement is called {@link TreeFilterProvider} because {@link TreeFilter}s as such are also
 * used as the "machinery" provided by the {@link org.revapi.ElementMatcher}s.
 *
 * A tree filter is meant to provide API tree traversal guidance to the callers. The {@link TreeFilter} gives a pretty
 * clear picture how that works.
 *
 * A tree filter provider is a simple extension interface that just supplies {@link TreeFilter} instances given its
 * configuration.
 *
 * Let's implement a simple tree filter for Java that will only keep elements from a configured package (and any sub
 * packages).
 */
public class PackageTreeFilterProvider implements TreeFilterProvider {
    private String packagePrefix;

    @Override
    public String getExtensionId() {
        return "pkg";
    }

    @Override
    public Reader getJSONSchema() {
        // our configuration is going to be a simple string - the package name prefix
        return new StringReader("{\"type\": \"string\"}");
    }

    @Override
    public void initialize(AnalysisContext analysisContext) {
        // find the configured minimum severity
        this.packagePrefix = analysisContext.getConfigurationNode().asText();
    }

    @Override
    public Optional<TreeFilter> filterFor(ArchiveAnalyzer analyzer) {
        if ("revapi.java".equals(analyzer.getApiAnalyzer().getExtensionId())) {
            return Optional.of(new IndependentTreeFilter() {
                @Override
                public FilterStartResult doStart(Element element) {
                    if (!(element instanceof JavaTypeElement)) {
                        // we let through anything that we don't know
                        return FilterStartResult.matchAndDescend();
                    }

                    PackageElement pkg = findPackage((JavaTypeElement) element);

                    // and try to match it with the configured prefix
                    if (pkg == null) {
                        // weird - every class should have an enclosing package
                        return FilterStartResult.doesntMatch();
                    }

                    boolean inPkg = pkg.getQualifiedName().toString().startsWith(packagePrefix);
                    return FilterStartResult.direct(FilterMatch.fromBoolean(inPkg), false);
                }
            });
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void close() {
    }

    private @Nullable PackageElement findPackage(JavaTypeElement element) {
        javax.lang.model.element.Element el = element.getDeclaringElement();
        while (el != null && !(el instanceof PackageElement)) {
            el = el.getEnclosingElement();
        }

        return (PackageElement) el;
    }
}
