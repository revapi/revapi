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
package org.revapi;

import static java.util.Collections.emptySet;

import static org.revapi.TransformationResult.Resolution.REPLACE;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * The result of an transformation. The result might or might not be resolved when the
 * {@link DifferenceTransform#tryTransform(Element, Element, Difference)} is called.
 */
public final class TransformationResult {
    private static final TransformationResult DISCARD = new TransformationResult(Resolution.DISCARD, emptySet());
    private static final TransformationResult KEEP = new TransformationResult(Resolution.KEEP, emptySet());
    private static final TransformationResult UNDECIDED = new TransformationResult(Resolution.UNDECIDED, emptySet());

    private final Resolution resolution;
    private final Set<Difference> differences;

    /**
     * @return result to keep the difference as is
     */
    public static TransformationResult keep() {
        return KEEP;
    }

    /**
     * @return result to discard the difference
     */
    public static TransformationResult discard() {
        return DISCARD;
    }

    /**
     * @return result to retry the transformation later due to lack of information at the moment
     */
    public static TransformationResult undecided() {
        return UNDECIDED;
    }

    /**
     * @param newDifferences
     *            the new set of differences to replace the old with
     * 
     * @return result to replace the difference with new ones
     */
    public static TransformationResult replaceWith(Set<Difference> newDifferences) {
        return new TransformationResult(REPLACE, newDifferences);
    }

    /**
     * Same as {@link #replaceWith(Set)} only for replacing with just a single difference.
     *
     * @param newDifference
     *            the difference to transform the original difference to
     * 
     * @return the transformation result
     */
    public static TransformationResult replaceWith(Difference newDifference) {
        return replaceWith(Collections.singleton(newDifference));
    }

    private TransformationResult(Resolution resolution, @Nullable Set<Difference> differences) {
        this.resolution = resolution;
        this.differences = differences;
    }

    /**
     * @return true if the transformation was resolved, false otherwise
     */
    public Resolution getResolution() {
        return resolution;
    }

    /**
     * The difference passed to the {@link DifferenceTransform#tryTransform(Element, Element, Difference)} method will
     * be replaced by the differences returned from this method.
     *
     * <p>
     * This method only returns meaningful data when the resolution is {@link Resolution#REPLACE}.
     */
    public Set<Difference> getDifferences() {
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
        TransformationResult that = (TransformationResult) o;
        return resolution == that.resolution && Objects.equals(differences, that.differences);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resolution, differences);
    }

    /**
     * The resolution of the difference transformation
     */
    public enum Resolution {
        DISCARD, KEEP, REPLACE, UNDECIDED
    }
}
