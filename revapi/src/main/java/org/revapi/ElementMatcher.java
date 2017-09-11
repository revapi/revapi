package org.revapi;

import java.util.function.Supplier;

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
     * @param recipe the recipe for the match. This might or might not be in an expected format for this matcher
     * @param element the element to match
     * @return true if the element matches the recipe, false if it decidedly doesn't, null if the decision cannot be
     * made in this round
     */
    Result matches(String recipe, Element element);

    enum Result {
        MATCH, DOESNT_MATCH, UNDECIDED;

        public static Result fromBoolean(boolean value) {
            return value ? MATCH : DOESNT_MATCH;
        }

        public Result and(Result other) {
            switch (this) {
                case MATCH:
                    return other;
                case DOESNT_MATCH:
                    return this;
                case UNDECIDED:
                    return other == DOESNT_MATCH ? other : this;
            }

            throw new IllegalStateException("Unhandled Result: " + this);
        }

        public Result and(Supplier<Result> other) {
            switch (this) {
                case MATCH:
                    return other.get();
                case DOESNT_MATCH:
                    return this;
                case UNDECIDED:
                    Result res = other.get();
                    return res == DOESNT_MATCH ? res : this;
            }

            throw new IllegalStateException("Unhandled Result: " + this);
        }

        public Result or(Result other) {
            switch (this) {
                case MATCH:
                    return this;
                case DOESNT_MATCH:
                    return other;
                case UNDECIDED:
                    return other == MATCH ? other : this;
            }

            throw new IllegalStateException("Unhandled Result: " + this);
        }

        public Result or(Supplier<Result> other) {
            switch (this) {
                case MATCH:
                    return this;
                case DOESNT_MATCH:
                    return other.get();
                case UNDECIDED:
                    Result res = other.get();
                    return res == MATCH ? res : this;
            }

            throw new IllegalStateException("Unhandled Result: " + this);
        }

        public Result negate() {
            switch (this) {
                case MATCH:
                    return DOESNT_MATCH;
                case DOESNT_MATCH:
                    return MATCH;
                case UNDECIDED:
                    return UNDECIDED;
            }

            throw new IllegalStateException("Unhandled Result: " + this);
        }
    }
}
