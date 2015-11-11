/*
 * Copyright 2015 Lukas Krejci
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

package org.revapi.basic;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.ElementFilter;

import org.jboss.dmr.ModelNode;

/**
 * An element filter that can filter out elements based on matching their full human readable representations.
 * Archive filter can filter out elements that belong to specified archives.
 *
 * <p>The configuration looks like follows:
 * <pre><code>
 * {
 *      "revapi" : {
 *          "filter" : {
 *              "elements" : {
 *                  "include" : ["REGEX_ON_ELEMENT_FULL_REPRESENTATIONS", "ANOTHER_REGEX_ON_ELEMENT_FULL_REPRESENTATIONS"],
 *                  "exclude" : ["REGEX_ON_ELEMENT_FULL_REPRESENTATIONS", "ANOTHER_REGEX_ON_ELEMENT_FULL_REPRESENTATIONS"]
 *              },
 *              "archives" : {
 *                  "include" : ["REGEX_ON_ARCHIVE_NAMES", "ANOTHER_REGEX_ON_ARCHIVE_NAMES"],
 *                  "exclude" : ["REGEX_ON_ARCHIVE_NAMES", "ANOTHER_REGEX_ON_ARCHIVE_NAMES"]
 *              }
 *          }
 *      }
 * }
 * </code></pre>
 * 
 * <p>If no include or exclude filters are defined, everything is included. If at least 1 include filter is defined, only
 * elements matching it are included. Out of the included elements, some may be further excluded by the exclude
 * filters.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public class ConfigurableElementFilter implements ElementFilter {
    private final List<Pattern> elementIncludes = new ArrayList<>();
    private final List<Pattern> elementExcludes = new ArrayList<>();
    private final List<Pattern> archiveIncludes = new ArrayList<>();
    private final List<Pattern> archiveExcludes = new ArrayList<>();

    private boolean doNothing;

    @Nullable
    @Override
    public String[] getConfigurationRootPaths() {
        return new String[]{"revapi.filter"};
    }

    @Nullable
    @Override
    public Reader getJSONSchema(@Nonnull String configurationRootPath) {
        if ("revapi.filter".equals(configurationRootPath)) {
            return new InputStreamReader(getClass().getResourceAsStream("/META-INF/filter-schema.json"),
                    Charset.forName("UTF-8"));
        } else {
            return null;
        }
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        ModelNode root = analysisContext.getConfiguration().get("revapi", "filter");
        if (!root.isDefined()) {
            doNothing = true;
            return;
        }

        ModelNode elements = root.get("elements");
        if (elements.isDefined()) {
            readFilter(elements, elementIncludes, elementExcludes);
        }

        ModelNode archives = root.get("archives");
        if (archives.isDefined()) {
            readFilter(archives, archiveIncludes, archiveExcludes);
        }

        doNothing = elementIncludes.isEmpty() && elementExcludes.isEmpty() && archiveIncludes.isEmpty() &&
                archiveExcludes.isEmpty();
    }

    @Override
    public boolean applies(@Nullable Element element) {
        if (doNothing) {
            return true;
        }

        String archive = element == null ? null : (element.getArchive() == null ? null :
            element.getArchive().getName());

        boolean include = true;
        if (archive != null) {
            include = isIncluded(archive, archiveIncludes, archiveExcludes);
        }

        if (include) {
            String representation = element == null ? null : element.getFullHumanReadableString();
            if (representation != null) {
                include = isIncluded(representation, elementIncludes, elementExcludes);
            }
        }

        return include;
    }

    @Override
    public boolean shouldDescendInto(@Nullable Object element) {
        return true;
    }

    @Override
    public void close() {
    }

    private static void readFilter(ModelNode root, List<Pattern> include, List<Pattern> exclude) {
        ModelNode includeNode = root.get("include");

        if (includeNode.isDefined()) {
            for (ModelNode inc : includeNode.asList()) {
                include.add(Pattern.compile(inc.asString()));
            }
        }

        ModelNode excludeNode = root.get("exclude");

        if (excludeNode.isDefined()) {
            for (ModelNode exc : excludeNode.asList()) {
                exclude.add(Pattern.compile(exc.asString()));
            }
        }
    }

    private static boolean isIncluded(String representation, List<Pattern> includePatterns, List<Pattern> excludePatterns) {
        boolean include = true;

        if (!includePatterns.isEmpty()) {
            include = false;
            for (Pattern p : includePatterns) {
                if (p.matcher(representation).matches()) {
                    include = true;
                    break;
                }
            }
        }

        if (include) {
            for (Pattern p : excludePatterns) {
                if (p.matcher(representation).matches()) {
                    include = false;
                    break;
                }
            }
        }

        return include;
    }
}
