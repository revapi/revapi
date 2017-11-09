package org.revapi;

import java.util.Optional;

import org.revapi.configuration.Configurable;

/**
 * An element matcher is a helper extension to other extensions that need to figure out if a certain
 * element meets certain criteria.
 *
 * @author Lukas Krejci
 */
public interface ElementMatcher extends Configurable, AutoCloseable {

    /**
     * Tries to compile the provided recipe into a form that can test individual elements.
     *
     * @param recipe the recipe to compile
     *
     * @return a compiled recipe or empty optional if the string cannot be compiled by this matcher
     */
    Optional<CompiledRecipe> compile(String recipe);

    interface CompiledRecipe {

        /**
         * Decides whether given element matches this recipe.
         *
         * <p>Note that the callers need to be able to retry the elements undecidable by this recipe again after
         * the whole element tree has been processed.
         *
         * @param element the element to match
         * @return a match result - {@link FilterMatch#UNDECIDED} means that the decision could not be made in this round
         */
        FilterMatch test(Element element);
    }
}
