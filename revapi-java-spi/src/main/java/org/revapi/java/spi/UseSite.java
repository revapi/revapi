package org.revapi.java.spi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.TypeElement;

import org.revapi.Element;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class UseSite {
    public enum Type {
        ANNOTATES, IS_INHERITED, IS_IMPLEMENTED, HAS_TYPE, RETURN_TYPE, PARAMETER_TYPE, IS_THROWN
    }

    /**
     * A visitor of the use site graph. Note that use sites form a directed <b>CYCLIC</b> graph so care must be taken
     * by the implementor of this interface to avoid infinite loops. The graph traversal code does <b>NOT</b> take
     * care of this automatically.
     *
     * @param <R> the type of the returned value when done visiting the graph
     * @param <P> the type of the parameter passed to the visitor
     */
    public interface Visitor<R, P> {

        /**
         * Visits the node in the use-site graph. Returning a non-null value will stop the traversal.
         *
         * @param type      the type that is being used
         * @param use       the site of the use of the type
         * @param parameter the parameter passed by the caller
         *
         * @return null if traversal should continue, a non-null result otherwise.
         */
        @Nullable
        R visit(@Nonnull TypeElement type, @Nonnull UseSite use, @Nullable P parameter);
    }

    private final Type useType;
    private final Element site;

    public UseSite(@Nonnull Type useType, @Nonnull Element site) {
        this.useType = useType;
        this.site = site;
    }

    @Nonnull
    public Element getSite() {
        return site;
    }

    @Nonnull
    public Type getUseType() {
        return useType;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("UseSite[");
        sb.append("site=").append(site);
        sb.append(", useType=").append(useType);
        sb.append(']');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UseSite useSite = (UseSite) o;

        if (!site.equals(useSite.site)) {
            return false;
        }

        if (useType != useSite.useType) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = useType.hashCode();
        result = 31 * result + site.hashCode();
        return result;
    }
}
