/*
 * Copyright 2014 Lukas Krejci
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

package org.revapi;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents the differences between comparable elements from old and new API.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Report {

    public static final class Builder {
        private Element oldElement;
        private Element newElement;
        ArrayList<Difference> differences = new ArrayList<>();

        @Nonnull
        public Builder withOld(@Nullable Element element) {
            oldElement = element;
            return this;
        }

        @Nonnull
        public Builder withNew(@Nullable Element element) {
            newElement = element;
            return this;
        }

        @Nonnull
        public Difference.Builder addProblem() {
            return new Difference.Builder(this);
        }

        @Nonnull
        public Report build() {
            return new Report(differences, oldElement, newElement);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final List<Difference> differences;
    private final Element oldElement;
    private final Element newElement;

    public Report(@Nonnull Iterable<Difference> problems, @Nullable Element oldElement,
        @Nullable Element newElement) {
        this.differences = new ArrayList<>();
        for (Difference p : problems) {
            this.differences.add(p);
        }

        this.oldElement = oldElement;
        this.newElement = newElement;
    }

    @Nullable
    public Element getNewElement() {
        return newElement;
    }

    @Nullable
    public Element getOldElement() {
        return oldElement;
    }

    @Nonnull
    public List<Difference> getDifferences() {
        return differences;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Report that = (Report) o;

        if (newElement != null ? !newElement.equals(that.newElement) : that.newElement != null) {
            return false;
        }

        if (oldElement != null ? !oldElement.equals(that.oldElement) : that.oldElement != null) {
            return false;
        }

        return differences.equals(that.differences);
    }

    @Override
    public int hashCode() {
        int result = differences.hashCode();
        result = 31 * result + (oldElement != null ? oldElement.hashCode() : 0);
        result = 31 * result + (newElement != null ? newElement.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MatchReport[");
        sb.append("oldElement=").append(oldElement == null ? "null" : oldElement.getFullHumanReadableString());
        sb.append(", newElement=").append(newElement == null ? "null" : newElement.getFullHumanReadableString());
        sb.append(", problems=").append(differences);
        sb.append(']');
        return sb.toString();
    }
}
