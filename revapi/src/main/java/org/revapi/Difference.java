/*
 * Copyright 2014-2020 Lukas Krejci
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
package org.revapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a single difference between an old and new API element.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Difference {
    private static abstract class BuilderBase<This extends BuilderBase<?>> {
        String code;
        String name;
        String description;
        Map<CompatibilityType, DifferenceSeverity> classification = new HashMap<>();
        Map<String, String> attachments = new LinkedHashMap<>(2);
        List<String> identifyingAttachments = new ArrayList<>(2);
        String justification;
        Criticality criticality;

        @Nonnull
        public This withCode(@Nonnull String code) {
            this.code = code;
            return castThis();
        }

        @Nonnull
        public This withName(@Nonnull String name) {
            this.name = name;
            return castThis();
        }

        @Nonnull
        public This withDescription(@Nullable String description) {
            this.description = description;
            return castThis();
        }

        @Nonnull
        public This addClassification(@Nonnull CompatibilityType compat, @Nonnull DifferenceSeverity severity) {
            classification.put(compat, severity);
            return castThis();
        }

        @Nonnull
        public This addClassifications(Map<CompatibilityType, DifferenceSeverity> classifications) {
            classification.putAll(classifications);
            return castThis();
        }

        public This clearClassifications() {
            classification.clear();
            return castThis();
        }

        @Nonnull
        public This addAttachment(@Nonnull String key, @Nonnull String value) {
            attachments.put(key, value);
            return castThis();
        }

        @Nonnull
        public This addAttachments(@Nonnull Map<String, String> attachments) {
            this.attachments.putAll(attachments);
            return castThis();
        }

        public This clearAttachments() {
            this.attachments.clear();
            return castThis();
        }

        @Nonnull
        public This withIdentifyingAttachments(@Nonnull List<String> attachments) {
            this.identifyingAttachments = attachments;
            return castThis();
        }

        public This withJustification(String justification) {
            this.justification = justification;
            return castThis();
        }

        public This withCriticality(Criticality criticality) {
            this.criticality = criticality;
            return castThis();
        }

        @SuppressWarnings("unchecked")
        private This castThis() {
            return (This) this;
        }
    }

    public static final class Builder extends BuilderBase<Builder> {

        private Builder() {

        }

        @Nonnull
        public Difference build() {
            return new Difference(code, name, description, justification, criticality, classification, attachments,
                    identifyingAttachments);
        }
    }

    public static final class InReportBuilder extends BuilderBase<InReportBuilder> {
        private final Report.Builder reportBuilder;

        InReportBuilder(Report.Builder reportBuilder) {
            this.reportBuilder = reportBuilder;
        }

        @Nonnull
        public Report.Builder done() {
            Difference p = new Difference(code, name, description, justification, criticality, classification,
                    attachments, identifyingAttachments);
            reportBuilder.differences.add(p);
            return reportBuilder;
        }
    }

    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    public static Builder copy(Difference other) {
        return builder()
                .withCode(other.code)
                .withDescription(other.description)
                .withName(other.name)
                .withJustification(other.justification)
                .withCriticality(other.criticality)
                .withIdentifyingAttachments(other.identifyingAttachments)
                .addAttachments(other.attachments)
                .addClassifications(other.classification);
    }

    /**
     * API analyzer dependent unique identification of the reported problem
     */
    public final String code;

    /**
     * Human readable name of the problem
     */
    public final String name;

    /**
     * Detailed description of the problem
     */
    public final String description;
    public final Map<CompatibilityType, DifferenceSeverity> classification;

    /**
     * The attachments of the difference, keyed by their meaning. Each difference can define a different set of
     * attachments that correspond to "findings" the difference represents. The map preserves the insertion order.
     */
    public final Map<String, String> attachments;

    private final List<String> identifyingAttachments;

    /**
     * The justification for this difference. This is meant to be read from the user-supplied configuration, set by
     * some difference transform and read by the reporters.
     */
    public final String justification;

    /**
     * The criticality of the difference. Never null when accessed in a {@link Reporter}.
     */
    public final @Nullable Criticality criticality;

    /**
     * @deprecated use the full constructor. This will be removed in some future release.
     */
    @Deprecated
    public Difference(@Nonnull String code, @Nonnull String name, @Nullable String description,
        @Nonnull CompatibilityType compatibility,
        @Nonnull DifferenceSeverity severity, @Nonnull Map<String, String> attachments) {
        this(code, name, description, Collections.singletonMap(compatibility, severity), attachments);
    }

    /**
     * @deprecated use the full constructor. This will be removed in some future release.
     */
    @Deprecated
    public Difference(@Nonnull String code, @Nonnull String name, @Nullable String description,
        @Nonnull Map<CompatibilityType, DifferenceSeverity> classification,
                      @Nonnull Map<String, String> attachments) {
        this(code, name, description, classification, attachments, Collections.emptyList());
    }

    /**
     * @deprecated use the full constructor. This will be removed in some future release.
     */
    @Deprecated
    public Difference(@Nonnull String code, @Nonnull String name, @Nullable String description,
            @Nonnull Map<CompatibilityType, DifferenceSeverity> classification,
            @Nonnull Map<String, String> attachments, @Nonnull List<String> identifyingAttachments) {
        this(code, name, description, null, null, classification, attachments, identifyingAttachments);
    }


    public Difference(@Nonnull String code, @Nonnull String name, @Nullable String description,
            @Nullable String justification, @Nullable Criticality criticality,
            @Nonnull Map<CompatibilityType, DifferenceSeverity> classification,
            @Nonnull Map<String, String> attachments, @Nonnull List<String> identifyingAttachments) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.justification = justification;
        this.criticality = criticality;
        HashMap<CompatibilityType, DifferenceSeverity> tmp = new HashMap<>(classification);
        this.classification = Collections.unmodifiableMap(tmp);
        this.attachments = Collections.unmodifiableMap(new LinkedHashMap<>(attachments));
        this.identifyingAttachments = Collections.unmodifiableList(identifyingAttachments);
    }

    public boolean isIdentifyingAttachment(String attachmentName) {
        return identifyingAttachments.contains(attachmentName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Difference that = (Difference) o;
        return code.equals(that.code) &&
                Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                classification.equals(that.classification) &&
                attachments.equals(that.attachments) &&
                Objects.equals(justification, that.justification);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, name, description, classification, attachments, justification);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Difference[");
        sb.append("code='").append(code).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append(", classification=").append(classification);
        sb.append(", description='").append(description).append('\'');
        sb.append(", justification='").append(justification).append('\'');
        sb.append(", criticality='").append(criticality).append('\'');
        sb.append(']');
        return sb.toString();
    }
}
