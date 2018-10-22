package org.revapi;

import static org.revapi.TransformationResult.Resolution.REPLACE;

import java.util.Collections;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * The result of an transformation. The result might or might not be resolved when the
 * {@link DifferenceTransform#tryTransform(Element, Element, Difference)} is called.
 */
public final class TransformationResult {
    private static final TransformationResult DISCARD = new TransformationResult(Resolution.DISCARD, null);
    private static final TransformationResult KEEP = new TransformationResult(Resolution.KEEP, null);
    private static final TransformationResult UNDECIDED = new TransformationResult(Resolution.UNDECIDED, null);

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
     * @param newDifferences the new set of differences to replace the old with
     * @return result to replace the difference with new ones
     */
    public static TransformationResult replaceWith(Set<Difference> newDifferences) {
        return new TransformationResult(REPLACE, newDifferences);
    }

    /**
     * Same as {@link #replaceWith(Set)} only for replacing with just a single difference.
     *
     * @param newDifference the difference to transform the original difference to
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
     * The difference passed to
     * the {@link DifferenceTransform#tryTransform(Element, Element, Difference)} method will be
     * replaced by the differences returned from this method.
     *
     * <p>{@code null} and an empty set are treated equivalently.
     *
     * <p>This method is only returns meaningful data when the resolution is {@link Resolution#REPLACE}.
     */
    public @Nullable Set<Difference> getDifferences() {
        return differences;
    }

    /**
     * The resolution of the difference transformation
     */
    public enum Resolution {
        DISCARD, KEEP, REPLACE, UNDECIDED
    }
}
