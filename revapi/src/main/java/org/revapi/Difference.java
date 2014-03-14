package org.revapi;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a single difference between an old and new API element.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Difference {
    public static final class Builder {
        private final Report.Builder reportBuilder;
        private String code;
        private String name;
        private String description;
        private Map<CompatibilityType, ChangeSeverity> classification = new HashMap<>();
        private List<Object> attachments = new ArrayList<>();

        Builder(Report.Builder reportBuilder) {
            this.reportBuilder = reportBuilder;
        }

        @Nonnull
        public Builder withCode(@Nonnull String code) {
            this.code = code;
            return this;
        }

        @Nonnull
        public Builder withName(@Nonnull String name) {
            this.name = name;
            return this;
        }

        @Nonnull
        public Builder withDescription(@Nullable String description) {
            this.description = description;
            return this;
        }

        @Nonnull
        public Builder addClassification(@Nonnull CompatibilityType compat, @Nonnull ChangeSeverity severity) {
            classification.put(compat, severity);
            return this;
        }

        @Nonnull
        public Builder addClassifications(Map<CompatibilityType, ChangeSeverity> classifications) {
            classification.putAll(classifications);
            return this;
        }

        @Nonnull
        public Builder addAttachment(@Nonnull Object attachment) {
            attachments.add(attachment);
            return this;
        }

        @Nonnull
        public Builder addAttachments(@Nonnull Iterable<?> attachments) {
            for (Object a : attachments) {
                this.attachments.add(a);
            }
            return this;
        }

        @Nonnull
        public Builder addAttachments(Object... attachments) {
            return addAttachments(Arrays.asList(attachments));
        }

        @Nonnull
        public Report.Builder done() {
            Difference p = build();
            reportBuilder.differences.add(p);
            return reportBuilder;
        }

        @Nonnull
        public Difference build() {
            return new Difference(code, name, description, classification, attachments);
        }
    }

    @Nonnull
    public static Builder builder() {
        return new Builder(null);
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
    public final Map<CompatibilityType, ChangeSeverity> classification;

    public final List<Object> attachments;

    public Difference(@Nonnull String code, @Nonnull String name, @Nullable String description,
        @Nonnull CompatibilityType compatibility,
        @Nonnull ChangeSeverity severity, @Nonnull List<Serializable> attachments) {
        this(code, name, description, Collections.singletonMap(compatibility, severity), attachments);
    }

    public Difference(@Nonnull String code, @Nonnull String name, @Nullable String description,
        @Nonnull Map<CompatibilityType, ChangeSeverity> classification, @Nonnull List<?> attachments) {
        this.code = code;
        this.name = name;
        this.description = description;
        HashMap<CompatibilityType, ChangeSeverity> tmp = new HashMap<>(classification);
        this.classification = Collections.unmodifiableMap(tmp);
        List<?> tmp2 = new ArrayList<>(attachments);
        this.attachments = Collections.unmodifiableList(tmp2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Difference difference = (Difference) o;

        return code.equals(difference.code) && classification.equals(difference.classification);
    }

    @Override
    public int hashCode() {
        int result = code.hashCode();
        result = 31 * result + classification.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Problem[");
        sb.append("code='").append(code).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append(", classification=").append(classification);
        sb.append(", description='").append(description).append('\'');
        sb.append(']');
        return sb.toString();
    }
}
