/*
 * Copyright 2014-2018 Lukas Krejci
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

import org.jboss.dmr.ModelNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class AnalysisConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(AnalysisConfiguration.class);

    private final MissingClassReporting missingClassReporting;
    private final Set<String> useReportingCodes;
    private final boolean ignoreMissingAnnotations;

    AnalysisConfiguration(MissingClassReporting missingClassReporting, Set<String> useReportingCodes,
                                 boolean ignoreMissingAnnotations) {
        this.missingClassReporting = missingClassReporting;
        this.useReportingCodes = useReportingCodes;
        this.ignoreMissingAnnotations = ignoreMissingAnnotations;
    }

    public static AnalysisConfiguration fromModel(ModelNode node) {
        MissingClassReporting reporting = readMissingClassReporting(node);
        Set<String> useReportingCodes = readUseReportingCodes(node);
        boolean ignoreMissingAnnotations = readIgnoreMissingAnnotations(node);

        ModelNode classesRegex = node.get("filter", "classes", "regex");
        ModelNode packagesRegex = node.get("filter", "packages", "regex");

        Set<Pattern> classInclusionFilters = readFilter(node.get("filter", "classes", "include"),
                classesRegex);
        Set<Pattern> classExclusionFilters = readFilter(node.get("filter", "classes", "exclude"),
                classesRegex);
        Set<Pattern> packageInclusionFilters = readFilter(node.get("filter", "packages", "include"),
                packagesRegex);
        Set<Pattern> packageExclusionFilters = readFilter(node.get("filter", "packages", "exclude"),
                packagesRegex);

        if (!(classInclusionFilters.isEmpty() && classExclusionFilters.isEmpty() && packageInclusionFilters.isEmpty()
                && packageExclusionFilters.isEmpty())) {
            LOG.warn("Filtering using the revapi.java.filter.* has been deprecated in favor of revapi.filter" +
                    " together with the java specific matchers (matcher.java).");
        }

        return new AnalysisConfiguration(reporting, useReportingCodes, ignoreMissingAnnotations);
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

    private static MissingClassReporting readMissingClassReporting(ModelNode analysisConfig) {
        ModelNode config = analysisConfig.get("missing-classes", "behavior");
        if (config.isDefined()) {
            switch (config.asString()) {
            case "report":
                return MissingClassReporting.REPORT;
            case "ignore":
                return MissingClassReporting.IGNORE;
            case "error":
                return MissingClassReporting.ERROR;
            default:
                throw new IllegalArgumentException("Unsupported value of revapi.java.missing-classes.behavior: '" +
                        config.asString() + "'. Only 'report', 'ignore' and 'error' are recognized.");
            }
        }

        return MissingClassReporting.REPORT;
    }

    private static boolean readIgnoreMissingAnnotations(ModelNode analysisConfig) {
        ModelNode config = analysisConfig.get("missing-classes", "ignoreMissingAnnotations");
        if (config.isDefined()) {
            return config.asBoolean();
        }

        return false;
    }

    private static Set<String> readUseReportingCodes(ModelNode analysisConfig) {
        Set<String> ret = new HashSet<>(5);
        ModelNode config = analysisConfig.get("reportUsesFor");
        if (config.isDefined()) {
            switch (config.getType()) {
                case LIST:
                    for (ModelNode code : config.asList()) {
                        ret.add(code.asString());
                    }
                    break;
                case STRING:
                    if ("all-differences".equals(config.asString())) {
                        ret = null;
                    }
                    break;
            }
        } else {
            ret.add("java.missing.oldClass");
            ret.add("java.missing.newClass");
            ret.add("java.class.nonPublicPartOfAPI");
            ret.add("java.class.externalClassExposedInAPI");
            ret.add("java.class.externalClassNoLongerExposedInAPI");
        }

        return ret;
    }

    private static Set<Pattern> readFilter(ModelNode filterNode, ModelNode regexNode) {
        if (!filterNode.isDefined()) {
            return Collections.emptySet();
        }

        boolean isRegex = regexNode.isDefined() && regexNode.asBoolean();

        return filterNode.asList().stream()
                .map(filter -> {
                    if (isRegex) {
                        return Pattern.compile(filter.asString());
                    } else {
                        return Pattern.compile(Pattern.quote(filter.asString()));
                    }
                })
                .collect(Collectors.toSet());
    }

    public enum MissingClassReporting {
        IGNORE, ERROR, REPORT
    }
}
