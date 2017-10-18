package org.revapi;

import org.revapi.configuration.Configurable;

/**
 * An element matcher is a helper extension to other extensions that need to figure out if a certain
 * element meets certain criteria.
 *
 * @author Lukas Krejci
 */
public interface ElementMatcher extends Configurable, AutoCloseable {

    /**
     * Decides whether given element matches the provided recipe.
     *
     * <p>Note that the callers need to be able to repeat the undecidable recipes again after the whole element tree
     * has been processed.
     *
     * @param recipe the recipe for the match. This might or might not be in an expected format for this matcher.
     *               If the recipe cannot be processed by this matcher, {@link FilterMatch#DOESNT_MATCH} ought to be
     *               returned.
     * @param element the element to match
     * @return a match result - {@link FilterMatch#UNDECIDED} means that the decision could not be made in this round
     */
    FilterMatch test(String recipe, Element element);

}
