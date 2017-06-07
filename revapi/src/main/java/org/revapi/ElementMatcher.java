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
     * @param recipe the recipe for the match. This might or might not be in an expected format for this matcher
     * @param element the element to match
     * @return true if the element matches the recipe, false otherwise
     */
    boolean matches(String recipe, Element element);
}
