/*
 * Copyright 2014-2023 Lukas Krejci
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
package org.revapi.java;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import org.revapi.TreeFilter;
import org.revapi.java.filters.ClassFilter;
import org.revapi.java.filters.PackageFilter;
import org.revapi.java.spi.JavaElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukas Krejci
 *
 * @since 0.1
 */
public final class AnalysisConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(AnalysisConfiguration.class);

    private final MissingClassReporting missingClassReporting;
    private final Set<String> useReportingCodes;
    private final boolean ignoreMissingAnnotations;
    private final boolean matchOverloads;
    private final TreeFilter<JavaElement> filter;

    AnalysisConfiguration(MissingClassReporting missingClassReporting, Set<String> useReportingCodes,
            boolean ignoreMissingAnnotations, boolean matchOverloads, @Nullable TreeFilter<JavaElement> filter) {
        this.missingClassReporting = missingClassReporting;
        this.useReportingCodes = useReportingCodes;
        this.ignoreMissingAnnotations = ignoreMissingAnnotations;
        this.matchOverloads = matchOverloads;
        this.filter = filter;
    }

    public static AnalysisConfiguration fromModel(JsonNode node) {
        MissingClassReporting reporting = readMissingClassReporting(node);
        Set<String> useReportingCodes = readUseReportingCodes(node);
        boolean ignoreMissingAnnotations = readIgnoreMissingAnnotations(node);
        boolean matchOverloads = readMatchOverloads(node);

        JsonNode classesRegex = node.path("filter").path("classes").path("regex");
        JsonNode packagesRegex = node.path("filter").path("packages").path("regex");

        Set<Pattern> classInclusionFilters = readFilter(node.path("filter").path("classes").path("include"),
                classesRegex);
        Set<Pattern> classExclusionFilters = readFilter(node.path("filter").path("classes").path("exclude"),
                classesRegex);
        Set<Pattern> packageInclusionFilters = readFilter(node.path("filter").path("packages").path("include"),
                packagesRegex);
        Set<Pattern> packageExclusionFilters = readFilter(node.path("filter").path("packages").path("exclude"),
                packagesRegex);

        TreeFilter<JavaElement> includeFilter = null;

        if (!(classInclusionFilters.isEmpty() && classExclusionFilters.isEmpty() && packageInclusionFilters.isEmpty()
                && packageExclusionFilters.isEmpty())) {
            LOG.warn("Filtering using the revapi.java.filter.(classes|packages) has been deprecated in favor of"
                    + " revapi.filter in combination with the java matcher.");

            if (!classInclusionFilters.isEmpty() || !classExclusionFilters.isEmpty()) {
                includeFilter = new ClassFilter(classInclusionFilters.toArray(new Pattern[0]),
                        classExclusionFilters.toArray(new Pattern[0]));
            }

            if (!packageInclusionFilters.isEmpty() || !packageExclusionFilters.isEmpty()) {
                PackageFilter pkgFilter = new PackageFilter(packageInclusionFilters.toArray(new Pattern[0]),
                        packageExclusionFilters.toArray(new Pattern[0]));
                if (includeFilter == null) {
                    includeFilter = pkgFilter;
                } else {
                    includeFilter = TreeFilter.intersection(includeFilter, pkgFilter);
                }
            }
        }

        return new AnalysisConfiguration(reporting, useReportingCodes, ignoreMissingAnnotations, matchOverloads,
                includeFilter);
    }

    public MissingClassReporting getMissingClassReporting() {
        return missingClassReporting;
    }

    public boolean reportUseForAllDifferences() {
        return useReportingCodes == null;
    }

    public Set<String> getUseReportingCodes() {
        return useReportingCodes == null ? Collections.emptySet() : useReportingCodes;
    }

    public boolean isIgnoreMissingAnnotations() {
        return ignoreMissingAnnotations;
    }

    public boolean isMatchOverloads() {
        return matchOverloads;
    }

    /**
     * @deprecated only supports the obsolete package and class name filtering before we can remove it.
     */
    @Deprecated
    @Nullable
    public TreeFilter<JavaElement> getPackageClassFilter() {
        return filter;
    }

    private static MissingClassReporting readMissingClassReporting(JsonNode analysisConfig) {
        JsonNode config = analysisConfig.path("missing-classes").path("behavior");
        if (config.isTextual()) {
            switch (config.asText()) {
            case "report":
                return MissingClassReporting.REPORT;
            case "ignore":
                return MissingClassReporting.IGNORE;
            case "error":
                return MissingClassReporting.ERROR;
            default:
                throw new IllegalArgumentException("Unsupported value of revapi.java.missing-classes.behavior: '"
                        + config.asText() + "'. Only 'report', 'ignore' and 'error' are recognized.");
            }
        }

        return MissingClassReporting.REPORT;
    }

    private static boolean readIgnoreMissingAnnotations(JsonNode analysisConfig) {
        JsonNode config = analysisConfig.path("missing-classes").path("ignoreMissingAnnotations");
        return config.asBoolean(false);
    }

    private static boolean readMatchOverloads(JsonNode analysisConfig) {
        return analysisConfig.path("matchOverloads").asBoolean(true);
    }

    private static @Nullable Set<String> readUseReportingCodes(JsonNode analysisConfig) {
        Set<String> ret = new HashSet<>(5);
        JsonNode config = analysisConfig.path("reportUsesFor");
        if (config.isArray()) {
            for (JsonNode code : config) {
                ret.add(code.asText());
            }
        } else if (config.isTextual() && "all-differences".equals(config.asText())) {
            ret = null;
        } else {
            ret.add("java.missing.oldClass");
            ret.add("java.missing.newClass");
            ret.add("java.class.nonPublicPartOfAPI");
            ret.add("java.class.externalClassExposedInAPI");
            ret.add("java.class.externalClassNoLongerExposedInAPI");
        }

        return ret;
    }

    private static Set<Pattern> readFilter(JsonNode filterNode, JsonNode regexNode) {
        if (!filterNode.isArray()) {
            return Collections.emptySet();
        }

        boolean isRegex = regexNode.asBoolean(false);

        return StreamSupport.stream(filterNode.spliterator(), false).map(filter -> {
            if (isRegex) {
                return Pattern.compile(filter.asText());
            } else {
                return Pattern.compile(Pattern.quote(filter.asText()));
            }
        }).collect(Collectors.toSet());
    }

    public enum MissingClassReporting {
        IGNORE, ERROR, REPORT
    }
}
