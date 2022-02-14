/*
 * Copyright 2014-2022 Lukas Krejci
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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.CompatibilityType;
import org.revapi.Criticality;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.Element;
import org.revapi.Reference;
import org.revapi.TransformationResult;
import org.revapi.base.BaseDifferenceTransform;

public class VersionsTransform<E extends Element<E>> extends BaseDifferenceTransform<E> {
    private static final Pattern[] ALL_CODES = new Pattern[] { Pattern.compile(".*") };
    private static final Pattern[] NO_CODES = new Pattern[0];

    private boolean enabled;
    private List<String> passThroughDifferences;
    private VersionIncreaseConfig allowedInMajor;
    private VersionIncreaseConfig allowedInMinor;
    private VersionIncreaseConfig allowedInPatch;
    private VersionIncreaseConfig allowedInSuffix;
    private DifferenceModification allowedModify;
    private DifferenceModification disallowedModify;
    private Map<String, VersionRecord> archiveHints;

    private static DifferenceSeverity getMaxSeverity(Difference diff) {
        return diff.classification.values().stream().max(Comparator.comparingInt(Enum::ordinal))
                .orElse(DifferenceSeverity.EQUIVALENT);
    }

    @Override
    public Pattern[] getDifferenceCodePatterns() {
        return enabled ? ALL_CODES : NO_CODES;
    }

    @Override
    public TransformationResult tryTransform(@Nullable E oldElement, @Nullable E newElement, Difference difference) {
        if (!enabled) {
            return TransformationResult.keep();
        }

        if (passThroughDifferences.contains(difference.code)) {
            return TransformationResult.keep();
        }

        Archive.Versioned oldArchive = (Archive.Versioned) (oldElement == null ? null : oldElement.getArchive());
        Archive.Versioned newArchive = (Archive.Versioned) (newElement == null ? null : newElement.getArchive());

        DifferenceSeverity maxSeverity = getMaxSeverity(difference);

        Archive.Versioned decidingArchive = newArchive == null ? oldArchive : newArchive;
        if (decidingArchive == null) {
            throw new IllegalStateException("At least one of the archives must not be null when comparing elements "
                    + oldElement + " and " + newElement);
        }

        boolean allowed;
        VersionRecord versionRecord = archiveHints.get(decidingArchive.getBaseName());
        if (versionRecord == null) {
            // the difference was found in archives that are not part of the primary API (i.e. the element is in some
            // supplementary archive). We need to find all elements in the primary API and decide about the change
            // from the point of view of the archive of the with the smallest version change.

            Set<Archive.Versioned> primaryReferencingArchives = new HashSet<>();
            if (newElement == null) {
                getAllReferencingAPIElements(oldElement, primaryReferencingArchives, new HashSet<>());
            } else {
                getAllReferencingAPIElements(newElement, primaryReferencingArchives, new HashSet<>());
            }

            versionRecord = primaryReferencingArchives.stream().map(a -> archiveHints.get(a.getBaseName())).reduce(null,
                    (a, b) -> {
                        if (a == null) {
                            return b;
                        } else if (b == null) {
                            return a;
                        } else {
                            return a.versionChange.compareTo(b.versionChange) > 0 ? b : a;
                        }
                    });

            if (versionRecord == null) {
                // this should really never happen, because we assume that any change in the supplementary archives
                // is only visible if there is a referencing element in the primary api. But let's just not throw any
                // exceptions and only communicate our findings to the user somehow.
                return TransformationResult.replaceWith(markUnhandled(difference));
            }
        }

        switch (versionRecord.versionChange) {
        case NEW:
            // either this is a truly new element in a new primary API archive or an element that has moved from
            // a supplementary archive into a new primary API archive.
            // In either case, we can allow this change purely from a versioning perspective.
            allowed = true;
            break;
        case REMOVED:
            if (newElement == null) {
                // the original archive is no longer in the API, and the element was really removed, so this is allowed.
                allowed = true;
            } else {
                // the archive disappeared but the element was moved to another archive. The only way this can happen
                // is that the element moved from a primary archives into a supplementary archive but is still exposed
                // in the API (it could not have been moved into a primary API archive, because we would have detected
                // that - the new archives take precedence when determining the change).
                //
                // But that would only be possible if the newElement didn't have an archive assigned - because otherwise
                // we would have found some archive for the new element. This case is not supported and we have to bail
                // somehow.
                return TransformationResult.replaceWith(markUnhandled(difference));
            }
            break;
        case SUFFIX:
            allowed = allowedInSuffix.allows(versionRecord, difference, maxSeverity);
            break;
        case PATCH:
            allowed = allowedInPatch.allows(versionRecord, difference, maxSeverity);
            break;
        case MINOR:
            allowed = allowedInMinor.allows(versionRecord, difference, maxSeverity);
            break;
        case MAJOR:
            allowed = allowedInMajor.allows(versionRecord, difference, maxSeverity);
            break;
        default:
            throw new IllegalStateException("Unhandled version change kind: " + versionRecord.versionChange);
        }

        if (allowed) {
            return allowedModify.modify(difference);
        } else {
            return disallowedModify.modify(difference);
        }
    }

    @Override
    public String getExtensionId() {
        return "revapi.versions";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("/META-INF/versions-schema.json"),
                StandardCharsets.UTF_8);
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        JsonNode config = analysisContext.getConfigurationNode();

        enabled = config.path("enabled").asBoolean(false);
        if (!enabled) {
            return;
        }

        Map<String, Archive.Versioned> oldArchives = StreamSupport
                .stream(analysisContext.getOldApi().getArchives().spliterator(), false).map(a -> (Archive.Versioned) a)
                .collect(Collectors.toMap(Archive.Versioned::getBaseName, identity()));
        Map<String, Archive.Versioned> newArchives = StreamSupport
                .stream(analysisContext.getNewApi().getArchives().spliterator(), false).map(a -> (Archive.Versioned) a)
                .collect(Collectors.toMap(Archive.Versioned::getBaseName, identity()));

        if (oldArchives.isEmpty() || newArchives.isEmpty()) {
            enabled = false;
            return;
        }

        passThroughDifferences = StreamSupport.stream(config.path("passThroughDifferences").spliterator(), false)
                .map(JsonNode::asText).collect(toList());

        allowedInMajor = VersionIncreaseConfig.parse(analysisContext,
                config.path("versionIncreaseAllows").path("major"));
        if (allowedInMajor == null) {
            allowedInMajor = VersionIncreaseConfig.DEFAULT_MAJOR;
        }

        allowedInMinor = VersionIncreaseConfig.parse(analysisContext,
                config.path("versionIncreaseAllows").path("minor"));
        if (allowedInMinor == null) {
            allowedInMinor = VersionIncreaseConfig.DEFAULT_MINOR;
        }

        allowedInPatch = VersionIncreaseConfig.parse(analysisContext,
                config.path("versionIncreaseAllows").path("patch"));
        if (allowedInPatch == null) {
            allowedInPatch = VersionIncreaseConfig.DEFAULT_PATCH;
        }

        allowedInSuffix = VersionIncreaseConfig.parse(analysisContext,
                config.path("versionIncreaseAllows").path("suffix"));
        if (allowedInSuffix == null) {
            allowedInSuffix = VersionIncreaseConfig.DEFAULT_SUFFIX;
        }

        allowedModify = DifferenceModification.parseModify(analysisContext, config.path("onAllowed"));
        if (allowedModify == null) {
            allowedModify = new DifferenceModification(emptyMap(), null, null, null,
                    singletonMap("breaksVersioningRules", "false"));
        }
        disallowedModify = DifferenceModification.parseModify(analysisContext, config.path("onDisallowed"));
        if (disallowedModify == null) {
            Criticality breakingCriticality = analysisContext.getDefaultCriticality(DifferenceSeverity.BREAKING);
            disallowedModify = new DifferenceModification(emptyMap(), breakingCriticality, null, null,
                    singletonMap("breaksVersioningRules", "true"));
        }

        boolean semantic0 = config.path("semantic0").asBoolean(true);

        boolean strictSemver = config.path("strictSemver").asBoolean(true);

        // now compute the change hints on the archives
        archiveHints = new HashMap<>();
        for (Map.Entry<String, Archive.Versioned> e : oldArchives.entrySet()) {
            Archive.Versioned oldArchive = e.getValue();
            Archive.Versioned newArchive = newArchives.remove(e.getKey());

            SemverVersion oldVersion = SemverVersion.parse(oldArchive.getVersion(), strictSemver);

            if (newArchive == null) {
                // the old archive is no longer part of the API and the classes from it are most probably part
                // of some other archive, possibly in the new API.
                // if we find some difference on some element from such archives, we can encounter:
                // 1) The old was removed (i.e. there is no counterpart anywhere in the new API)
                // 2) The old exists in other archive with some changes
                //
                // In the first case, we should not allow any severity on the old archive. In the second case,
                // we should decide on the semver result based on the version change of the new archive.
                //
                // Therefore, here we just assume the first case. The second case is automatically handled by
                // the fact that we prioritize the new element when looking for the archive in tryTransform().
                archiveHints.put(oldArchive.getBaseName(),
                        new VersionRecord(oldArchive, null, oldVersion, null, VersionChange.REMOVED));
                continue;
            }

            SemverVersion newVersion = SemverVersion.parse(newArchive.getVersion(), strictSemver);

            boolean majorIncrease = oldVersion.major < newVersion.major;
            boolean minorIncrease = oldVersion.major == newVersion.major && oldVersion.minor < newVersion.minor;
            boolean patchIncrease = oldVersion.major == newVersion.major && oldVersion.minor == newVersion.minor
                    && oldVersion.patch < newVersion.patch;
            boolean suffixChange = oldVersion.major == newVersion.major && oldVersion.minor == newVersion.minor
                    && oldVersion.patch == newVersion.patch && !Objects.equals(oldVersion.suffix, newVersion.suffix);

            VersionChange versionChange;
            if (semantic0 && oldVersion.major == 0 && !majorIncrease) {
                if (minorIncrease) {
                    versionChange = VersionChange.MAJOR;
                } else if (patchIncrease) {
                    versionChange = VersionChange.MAJOR;
                } else if (suffixChange) {
                    versionChange = VersionChange.MAJOR;
                } else {
                    versionChange = VersionChange.NEW;
                }
            } else {
                if (majorIncrease) {
                    versionChange = VersionChange.MAJOR;
                } else if (minorIncrease) {
                    versionChange = VersionChange.MINOR;
                } else if (patchIncrease) {
                    versionChange = VersionChange.PATCH;
                } else if (suffixChange) {
                    versionChange = VersionChange.SUFFIX;
                } else {
                    versionChange = VersionChange.NEW;
                }
            }

            archiveHints.put(newArchive.getBaseName(),
                    new VersionRecord(oldArchive, newArchive, oldVersion, newVersion, versionChange));
        }

        // process the archives in the new API that are not present in the old API
        for (Map.Entry<String, Archive.Versioned> e : newArchives.entrySet()) {
            SemverVersion newVersion = SemverVersion.parse(e.getValue().getVersion(), strictSemver);

            // There are again 2 cases of the element that we can encounter:
            // 1) The element is brand new (no counter part in any of the archives of the old API)
            // 2) The element has moved from another archive with changes
            archiveHints.put(e.getKey(), new VersionRecord(null, e.getValue(), null, newVersion, VersionChange.NEW));
        }

    }

    private static Difference markUnhandled(Difference orig) {
        return Difference.copy(orig).addAttachment("breaksVersioningRules", "unknown").build();
    }

    private void getAllReferencingAPIElements(@Nullable E element, Set<Archive.Versioned> results, Set<E> processed) {
        if (element == null || processed.contains(element)) {
            return;
        }

        processed.add(element);

        if (element.getApi().getArchiveRole(element.getArchive()) == Archive.Role.PRIMARY) {
            results.add((Archive.Versioned) element.getArchive());
        }

        E parent = element.getParent();
        if (parent != null) {
            getAllReferencingAPIElements(parent, results, processed);
        }

        for (Reference<E> reference : element.getReferencingElements()) {
            E el = reference.getElement();
            getAllReferencingAPIElements(el, results, processed);
        }
    }

    private static class VersionIncreaseConfig {
        static final VersionIncreaseConfig DEFAULT_MAJOR = new VersionIncreaseConfig(singletonList(new IncreaseAllows(
                false, SeverityCheck.BREAKING, null, null, null, emptyMap(), emptyMap(), emptyList(), null, null)));
        static final VersionIncreaseConfig DEFAULT_MINOR = new VersionIncreaseConfig(
                singletonList(new IncreaseAllows(false, SeverityCheck.NON_BREAKING, null, null, null, emptyMap(),
                        emptyMap(), emptyList(), null, null)));;
        static final VersionIncreaseConfig DEFAULT_PATCH = new VersionIncreaseConfig(singletonList(new IncreaseAllows(
                false, SeverityCheck.EQUIVALENT, null, null, null, emptyMap(), emptyMap(), emptyList(), null, null)));;
        static final VersionIncreaseConfig DEFAULT_SUFFIX = new VersionIncreaseConfig(singletonList(new IncreaseAllows(
                false, SeverityCheck.EQUIVALENT, null, null, null, emptyMap(), emptyMap(), emptyList(), null, null)));;

        final List<IncreaseAllows> allows;

        static @Nullable VersionIncreaseConfig parse(AnalysisContext ctx, JsonNode node) {
            if (node.isMissingNode()) {
                return null;
            }

            List<IncreaseAllows> parsedAllows = new ArrayList<>();
            if (node.isObject()) {
                parsedAllows.add(parseAllows(ctx, node));
            } else if (node.isArray()) {
                for (JsonNode allows : node) {
                    IncreaseAllows a = parseAllows(ctx, allows);
                    if (a != null) {
                        parsedAllows.add(a);
                    }
                }
            } else {
                throw new IllegalArgumentException(
                        "Expecting an object or array when specifying the allowed changes in a version increase.");
            }

            return new VersionIncreaseConfig(parsedAllows);
        }

        private static @Nullable IncreaseAllows parseAllows(AnalysisContext ctx, JsonNode node) {
            if (!node.isObject()) {
                return null;
            }

            boolean regex = node.path("regex").asBoolean(false);
            @Nullable
            String severity = node.path("severity").asText(null);
            @Nullable
            String criticality = node.path("criticality").asText(null);
            @Nullable
            String code = node.path("code").asText(null);
            @Nullable
            String justification = node.path("justification").asText(null);
            Map<String, String> attachments = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = node.path("attachments").fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                attachments.put(entry.getKey(), entry.getValue().asText());
            }

            Map<CompatibilityType, DifferenceSeverity> classification = new EnumMap<>(CompatibilityType.class);
            it = node.path("classification").fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                classification.put(CompatibilityType.valueOf(entry.getKey()),
                        DifferenceSeverity.valueOf(entry.getValue().asText()));
            }

            @Nullable
            String oldSuffix = node.path("old").asText(null);
            @Nullable
            String newSuffix = node.path("new").asText(null);

            List<String> inArchives = StreamSupport.stream(node.path("inArchives").spliterator(), false)
                    .map(JsonNode::asText).collect(toList());

            return new IncreaseAllows(regex, severity == null ? null : SeverityCheck.valueOf(severity),
                    criticality == null ? null : ctx.getCriticalityByName(criticality), code, justification,
                    attachments, classification, inArchives, oldSuffix, newSuffix);
        }

        VersionIncreaseConfig(List<IncreaseAllows> allows) {
            this.allows = allows;
        }

        boolean allows(VersionRecord versionRecord, Difference difference, DifferenceSeverity maxSeverity) {
            return allows.stream().reduce(false, (a, b) -> a || b.allows(versionRecord, difference, maxSeverity),
                    Boolean::logicalOr);
        }
    }

    private static class IncreaseAllows {
        final @Nullable SeverityCheck severity;
        final @Nullable Criticality criticality;
        final @Nullable Pattern code;
        final @Nullable Pattern justification;
        final Map<String, Pattern> attachments;
        final Map<CompatibilityType, DifferenceSeverity> classification;
        final List<Pattern> inArchives;
        final @Nullable Pattern oldSuffix;
        final @Nullable Pattern newSuffix;

        private IncreaseAllows(boolean regex, @Nullable SeverityCheck severity, @Nullable Criticality criticality,
                @Nullable String code, @Nullable String justification, Map<String, String> attachments,
                Map<CompatibilityType, DifferenceSeverity> classification, List<String> inArchives,
                @Nullable String oldSuffix, @Nullable String newSuffix) {
            this.severity = severity;
            this.criticality = criticality;
            this.code = code == null ? null : Pattern.compile(regex ? code : Pattern.quote(code));
            this.justification = justification == null ? null
                    : Pattern.compile(regex ? justification : Pattern.quote(justification));
            this.attachments = attachments.entrySet().stream().collect(
                    toMap(Map.Entry::getKey, e -> Pattern.compile(regex ? e.getValue() : Pattern.quote(e.getValue()))));
            this.classification = classification;
            this.inArchives = inArchives.stream().map(a -> Pattern.compile(regex ? a : Pattern.quote(a)))
                    .collect(toList());
            this.oldSuffix = oldSuffix == null ? null : Pattern.compile(regex ? oldSuffix : Pattern.quote(oldSuffix));
            this.newSuffix = newSuffix == null ? null : Pattern.compile(regex ? newSuffix : Pattern.quote(newSuffix));
        }

        boolean allows(VersionRecord versionRecord, Difference difference, DifferenceSeverity maxSeverity) {

            if (versionRecord.oldArchive == null && versionRecord.newArchive == null) {
                throw new IllegalStateException("At least one of the archives must be non-null.");
            }

            // first we need to match the additional criteria on the archive
            if (!inArchives.isEmpty()) {
                if (inArchives.stream().noneMatch(p -> p.matcher(versionRecord.newArchive == null
                        ? versionRecord.oldArchive.getBaseName() : versionRecord.newArchive.getBaseName()).matches())) {
                    return false;
                }
            }

            if (this.oldSuffix != null) {
                if (versionRecord.oldVersion == null) {
                    return false;
                } else {
                    String suffix = versionRecord.oldVersion.suffix;
                    if (!oldSuffix.matcher(suffix == null ? "" : suffix).matches()) {
                        return false;
                    }
                }
            }

            if (this.newSuffix != null) {
                if (versionRecord.newVersion == null) {
                    return false;
                } else {
                    String suffix = versionRecord.newVersion.suffix;
                    if (!newSuffix.matcher(suffix == null ? "" : suffix).matches()) {
                        return false;
                    }
                }
            }

            // now we can check the difference

            if (this.severity != null && !this.severity.allows(maxSeverity)) {
                return false;
            }

            if (this.criticality != null && difference.criticality != null
                    && this.criticality.getLevel() < difference.criticality.getLevel()) {
                return false;
            }

            if (this.code != null && !this.code.matcher(difference.code).matches()) {
                return false;
            }

            if (this.justification != null && !this.justification
                    .matcher(difference.justification == null ? "" : difference.justification).matches()) {
                return false;
            }

            if (this.attachments != null) {
                for (Map.Entry<String, Pattern> e : attachments.entrySet()) {
                    String key = e.getKey();
                    Pattern pattern = e.getValue();

                    String value = difference.attachments.get(key);

                    if (!pattern.matcher(value == null ? "" : value).matches()) {
                        return false;
                    }
                }
            }

            if (this.classification != null) {
                for (Map.Entry<CompatibilityType, DifferenceSeverity> e : classification.entrySet()) {
                    DifferenceSeverity expected = e.getValue();
                    DifferenceSeverity actual = difference.classification.get(e.getKey());
                    if (actual != null && expected.compareTo(actual) < 0) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    private static class DifferenceModification {
        final Map<CompatibilityType, DifferenceSeverity> classification;
        final @Nullable Criticality criticality;
        final @Nullable TextModification justification;
        final @Nullable TextModification description;
        final Map<String, String> attachments;
        final boolean remove;

        private static @Nullable DifferenceModification parseModify(AnalysisContext ctx, JsonNode node) {
            if (node.isMissingNode()) {
                return null;
            }

            if (node.path("remove").asBoolean(false)) {
                return new DifferenceModification();
            }

            JsonNode classificationNode = node.path("classification");
            Map<CompatibilityType, DifferenceSeverity> classification = new EnumMap<>(CompatibilityType.class);
            if (classificationNode.has("SOURCE")) {
                classification.put(CompatibilityType.SOURCE,
                        DifferenceSeverity.valueOf(classificationNode.get("SOURCE").asText()));
            }
            if (classificationNode.has("BINARY")) {
                classification.put(CompatibilityType.BINARY,
                        DifferenceSeverity.valueOf(classificationNode.get("BINARY").asText()));
            }
            if (classificationNode.has("SEMANTIC")) {
                classification.put(CompatibilityType.SEMANTIC,
                        DifferenceSeverity.valueOf(classificationNode.get("SEMANTIC").asText()));
            }
            if (classificationNode.has("OTHER")) {
                classification.put(CompatibilityType.OTHER,
                        DifferenceSeverity.valueOf(classificationNode.get("OTHER").asText()));
            }

            Criticality criticality;
            if (!node.path("criticality").isMissingNode()) {
                criticality = ctx.getCriticalityByName(node.path("criticality").asText());
            } else {
                criticality = null;
            }

            TextModification justification = parseTextModification(node.path("justification"));
            TextModification description = parseTextModification(node.path("description"));

            JsonNode attachmentsNode = node.path("attachments");
            Map<String, String> attachments = new HashMap<>();
            if (!attachmentsNode.isMissingNode()) {
                Iterator<Map.Entry<String, JsonNode>> it = attachmentsNode.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> entry = it.next();
                    attachments.put(entry.getKey(), entry.getValue().asText());
                }
            }

            return new DifferenceModification(classification, criticality, justification, description, attachments);
        }

        @Nullable
        private static TextModification parseTextModification(JsonNode modificationNode) {
            if (modificationNode.isTextual()) {
                return new TextModification(modificationNode.asText(), null, null);
            } else if (modificationNode.isObject()) {
                String prepend = modificationNode.path("prepend").asText();
                String append = modificationNode.path("append").asText();
                return new TextModification(null, prepend, append);
            } else {
                return null;
            }
        }

        private DifferenceModification() {
            this.remove = true;
            this.classification = emptyMap();
            this.criticality = null;
            this.justification = null;
            this.description = null;
            this.attachments = null;
        }

        private DifferenceModification(Map<CompatibilityType, DifferenceSeverity> classification,
                @Nullable Criticality criticality, @Nullable TextModification justification,
                @Nullable TextModification description, Map<String, String> attachments) {
            this.classification = classification;
            this.criticality = criticality;
            this.justification = justification;
            this.description = description;
            this.attachments = attachments;
            this.remove = false;
        }

        TransformationResult modify(Difference difference) {
            if (remove) {
                return TransformationResult.discard();
            }

            Difference.Builder bld = Difference.copy(difference);
            boolean changed = false;

            if (!classification.isEmpty()) {
                changed = true;
                bld.addClassifications(classification);
            }

            if (criticality != null) {
                changed = true;
                bld.withCriticality(criticality);
            }

            if (justification != null) {
                changed = true;
                bld.withJustification(justification.apply(difference.justification));
            }

            if (description != null) {
                changed = true;
                bld.withDescription(description.apply(difference.description));
            }

            if (!attachments.isEmpty()) {
                changed = true;
                bld.addAttachments(attachments);
            }

            if (changed) {
                Difference newDiff = bld.build();
                if (newDiff.equals(difference)) {
                    return TransformationResult.keep();
                } else {
                    return TransformationResult.replaceWith(bld.build());
                }
            } else {
                return TransformationResult.keep();
            }
        }
    }

    private enum SeverityCheck {
        NONE, EQUIVALENT, NON_BREAKING, POTENTIALLY_BREAKING, BREAKING;

        boolean allows(DifferenceSeverity severity) {
            return this.ordinal() >= severity.ordinal() + 1;
        }
    }

    private static class TextModification {
        final @Nullable String value;
        final @Nullable String prepend;
        final @Nullable String append;

        private TextModification(@Nullable String value, @Nullable String prepend, @Nullable String append) {
            this.value = value;
            this.prepend = prepend;
            this.append = append;
        }

        @Nullable
        String apply(@Nullable String value) {
            if (this.value != null) {
                value = this.value;
            }

            if (this.prepend != null) {
                if (value == null) {
                    value = this.prepend;
                } else if (!value.startsWith(this.prepend)) {
                    value = this.prepend + value;
                }
            }

            if (this.append != null) {
                if (value == null) {
                    value = this.append;
                } else if (!value.endsWith(this.append)) {
                    value = value + this.append;
                }
            }

            return value;
        }
    }

    private static final class VersionRecord {
        final @Nullable Archive.Versioned oldArchive;
        final @Nullable Archive.Versioned newArchive;
        final @Nullable SemverVersion oldVersion;
        final @Nullable SemverVersion newVersion;
        final VersionChange versionChange;

        VersionRecord(@Nullable Archive.Versioned oldArchive, @Nullable Archive.Versioned newArchive,
                @Nullable SemverVersion oldVersion, @Nullable SemverVersion newVersion, VersionChange versionChange) {
            this.oldArchive = oldArchive;
            this.newArchive = newArchive;
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
            this.versionChange = versionChange;
        }
    }

    private enum VersionChange {
        REMOVED, NEW, SUFFIX, PATCH, MINOR, MAJOR;
    }
}
