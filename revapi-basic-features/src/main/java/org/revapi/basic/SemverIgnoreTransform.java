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
package org.revapi.basic;

import static java.util.stream.Collectors.toList;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.CompatibilityType;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.DifferenceTransform;
import org.revapi.Element;
import org.revapi.TransformationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukas Krejci
 *
 * @since 0.3.7
 *
 * @deprecated use the {@link VersionsTransform} instead
 */
@Deprecated
public class SemverIgnoreTransform<E extends Element<E>> implements DifferenceTransform<E> {
    private static final Logger LOG = LoggerFactory.getLogger(SemverIgnoreTransform.class);

    private boolean enabled;
    private DifferenceSeverity allowedSeverity;
    private List<String> passThroughDifferences;

    @Nonnull
    @Override
    public Pattern[] getDifferenceCodePatterns() {
        return enabled ? new Pattern[] { Pattern.compile(".*") } : new Pattern[0];
    }

    @Override
    public TransformationResult tryTransform(@Nullable E oldElement, @Nullable E newElement, Difference difference) {
        if (!enabled) {
            return TransformationResult.keep();
        }

        if (passThroughDifferences.contains(difference.code)) {
            return TransformationResult.keep();
        }

        if (allowedSeverity == null) {
            return TransformationResult.replaceWith(asBreaking(difference));
        } else if (allowedSeverity == DifferenceSeverity.BREAKING) {
            return TransformationResult.discard();
        } else {
            DifferenceSeverity diffSeverity = getMaxSeverity(difference);
            if (allowedSeverity.ordinal() - diffSeverity.ordinal() >= 0) {
                return TransformationResult.discard();
            } else {
                return TransformationResult.replaceWith(asBreaking(difference));
            }
        }
    }

    private Difference asBreaking(Difference d) {
        Difference.Builder bld = Difference.copy(d)
                .addClassification(CompatibilityType.OTHER, DifferenceSeverity.BREAKING)
                .addAttachment("breaksSemanticVersioning", "true");

        if (d.description == null || !d.description.endsWith("(breaks semantic versioning)")) {
            bld.withDescription(d.description == null ? "(breaks semantic versioning)"
                    : (d.description + " (breaks semantic versioning)"));
        }

        if (!d.name.startsWith("Incompatible with the current version: ")) {
            bld.withName("Incompatible with the current version: " + d.name);
        }

        return bld.build();
    }

    private DifferenceSeverity getMaxSeverity(Difference diff) {
        return diff.classification.values().stream().max((d1, d2) -> d1.ordinal() - d2.ordinal()).get();
    }

    @Override
    public void close() throws Exception {
    }

    @Nullable
    @Override
    public String getExtensionId() {
        return "revapi.semver.ignore";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("/META-INF/semver-ignore-schema.json"),
                StandardCharsets.UTF_8);
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        JsonNode node = analysisContext.getConfigurationNode();

        enabled = node.path("enabled").asBoolean(false);

        if (enabled) {
            if (hasMultipleElements(analysisContext.getOldApi().getArchives())
                    || hasMultipleElements(analysisContext.getNewApi().getArchives())) {
                throw new IllegalArgumentException(
                        "The semver extension doesn't handle changes in multiple archives at once.");
            }

            Iterator<? extends Archive> oldArchives = analysisContext.getOldApi().getArchives().iterator();
            Iterator<? extends Archive> newArchives = analysisContext.getNewApi().getArchives().iterator();

            if (!oldArchives.hasNext() || !newArchives.hasNext()) {
                enabled = false;
                return;
            }

            Archive oldArchive = oldArchives.next();
            Archive newArchive = newArchives.next();

            if (!(oldArchive instanceof Archive.Versioned)) {
                throw new IllegalArgumentException("Old archive doesn't support extracting the version.");
            }

            if (!(newArchive instanceof Archive.Versioned)) {
                throw new IllegalArgumentException("New archive doesn't support extracting the version.");
            }

            String oldVersionString = ((Archive.Versioned) oldArchive).getVersion();
            String newVersionString = ((Archive.Versioned) newArchive).getVersion();

            SemverVersion oldVersion = SemverVersion.parse(oldVersionString, true);
            SemverVersion newVersion = SemverVersion.parse(newVersionString, true);

            if (newVersion.major == 0 && oldVersion.major == 0 && !node.hasNonNull("versionIncreaseAllows")) {
                DifferenceSeverity minorChangeAllowed = asSeverity(node.path("versionIncreaseAllows").path("minor"),
                        DifferenceSeverity.BREAKING);
                DifferenceSeverity patchVersionAllowed = asSeverity(node.path("versionIncreaseAllows").path("patch"),
                        DifferenceSeverity.NON_BREAKING);

                if (newVersion.minor > oldVersion.minor) {
                    allowedSeverity = minorChangeAllowed;
                } else if (newVersion.minor == oldVersion.minor && newVersion.patch > oldVersion.patch) {
                    allowedSeverity = patchVersionAllowed;
                } else {
                    allowedSeverity = null;
                }
            } else {
                DifferenceSeverity majorChangeAllowed = asSeverity(node.path("versionIncreaseAllows").path("major"),
                        DifferenceSeverity.BREAKING);
                DifferenceSeverity minorChangeAllowed = asSeverity(node.path("versionIncreaseAllows").path("minor"),
                        DifferenceSeverity.NON_BREAKING);
                DifferenceSeverity patchVersionAllowed = asSeverity(node.path("versionIncreaseAllows").path("patch"),
                        DifferenceSeverity.EQUIVALENT);

                if (newVersion.major > oldVersion.major) {
                    allowedSeverity = majorChangeAllowed;
                } else if (newVersion.major == oldVersion.major && newVersion.minor > oldVersion.minor) {
                    allowedSeverity = minorChangeAllowed;
                } else {
                    allowedSeverity = patchVersionAllowed;
                }
            }

            passThroughDifferences = Collections.emptyList();
            if (node.hasNonNull("passThroughDifferences")) {
                passThroughDifferences = StreamSupport.stream(node.path("passThroughDifferences").spliterator(), false)
                        .map(JsonNode::asText).collect(toList());
            }
        }

        if (enabled) {
            LOG.warn("revapi.semver.ignore transform has been deprecated. Please use the new revapi.versions which has"
                    + " better integration with the new Revapi features.");
        }
    }

    private boolean hasMultipleElements(Iterable<?> it) {
        Iterator<?> i = it.iterator();
        if (!i.hasNext()) {
            return false;
        }

        i.next();

        return i.hasNext();
    }

    private static DifferenceSeverity asSeverity(JsonNode configNode, DifferenceSeverity defaultValue) {
        if (configNode.isMissingNode()) {
            return defaultValue;
        } else {
            switch (configNode.asText()) {
            case "none":
                return null;
            case "equivalent":
                return DifferenceSeverity.EQUIVALENT;
            case "nonBreaking":
                return DifferenceSeverity.NON_BREAKING;
            case "potentiallyBreaking":
                return DifferenceSeverity.POTENTIALLY_BREAKING;
            case "breaking":
                return DifferenceSeverity.BREAKING;
            default:
                return defaultValue;
            }
        }
    }
}
