/*
 * Copyright 2014 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class MatchReport {
    public static final class Problem {
        public static final class Builder {
            private final MatchReport.Builder reportBuilder;
            private String code;
            private String name;
            private String description;
            private Map<CompatibilityType, ChangeSeverity> classification = new HashMap<>();
            private List<Object> attachments = new ArrayList<>();

            private Builder(MatchReport.Builder reportBuilder) {
                this.reportBuilder = reportBuilder;
            }

            public Builder withCode(String code) {
                this.code = code;
                return this;
            }

            public Builder withName(String name) {
                this.name = name;
                return this;
            }

            public Builder withDescription(String description) {
                this.description = description;
                return this;
            }

            public Builder addClassification(CompatibilityType compat, ChangeSeverity severity) {
                classification.put(compat, severity);
                return this;
            }

            public Builder addAttachment(Object attachment) {
                attachments.add(attachment);
                return this;
            }

            public Builder addAttachments(Iterable<?> attachments) {
                for (Object a : attachments) {
                    this.attachments.add(a);
                }
                return this;
            }

            public Builder addAttachments(Object... attachments) {
                return addAttachments(Arrays.asList(attachments));
            }

            public MatchReport.Builder done() {
                Problem p = build();
                reportBuilder.problems.add(p);
                return reportBuilder;
            }

            public Problem build() {
                return new Problem(code, name, description, classification, attachments);
            }
        }

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

        public Problem(String code, String name, String description, CompatibilityType compatibility,
            ChangeSeverity severity, List<Serializable> attachments) {
            this(code, name, description, Collections.singletonMap(compatibility, severity), attachments);
        }

        public Problem(String code, String name, String description,
            Map<CompatibilityType, ChangeSeverity> classification, List<?> attachments) {
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

            Problem problem = (Problem) o;

            return code.equals(problem.code) && classification.equals(problem.classification);
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

    public static final class Builder {
        private Element oldElement;
        private Element newElement;
        private ArrayList<Problem> problems = new ArrayList<>();

        public Builder withOld(Element element) {
            oldElement = element;
            return this;
        }

        public Builder withNew(Element element) {
            newElement = element;
            return this;
        }

        public Problem.Builder addProblem() {
            return new Problem.Builder(this);
        }

        public MatchReport build() {
            return new MatchReport(problems, oldElement, newElement);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final List<Problem> problems;
    private final Element oldElement;
    private final Element newElement;

    public MatchReport(Iterable<Problem> problems, Element oldElement, Element newElement) {
        this.problems = new ArrayList<>();
        for (Problem p : problems) {
            this.problems.add(p);
        }

        this.oldElement = oldElement;
        this.newElement = newElement;
    }

    public Element getNewElement() {
        return newElement;
    }

    public Element getOldElement() {
        return oldElement;
    }

    public List<Problem> getProblems() {
        return problems;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MatchReport that = (MatchReport) o;

        if (newElement != null ? !newElement.equals(that.newElement) : that.newElement != null) {
            return false;
        }

        if (oldElement != null ? !oldElement.equals(that.oldElement) : that.oldElement != null) {
            return false;
        }

        return problems.equals(that.problems);
    }

    @Override
    public int hashCode() {
        int result = problems.hashCode();
        result = 31 * result + (oldElement != null ? oldElement.hashCode() : 0);
        result = 31 * result + (newElement != null ? newElement.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MatchReport[");
        sb.append("oldElement=").append(oldElement == null ? "null" : oldElement.getFullHumanReadableString());
        sb.append(", newElement=").append(newElement == null ? "null" : newElement.getFullHumanReadableString());
        sb.append(", problems=").append(problems);
        sb.append(']');
        return sb.toString();
    }
}
