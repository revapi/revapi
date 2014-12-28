package org.revapi.java.spi;

import java.util.Arrays;
import java.util.EnumSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.TypeElement;

import org.revapi.Element;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class UseSite {

    /**
     * The way the used class is used by the use site.
     */
    public enum Type {
        /**
         * The used class annotates the use site.
         */
        ANNOTATES,

        /**
         * The used class is inherited by the use site (class).
         */
        IS_INHERITED,

        /**
         * The used class is implemented by the use site (class).
         */
        IS_IMPLEMENTED,

        /**
         * The use site (field) has the type of the used class.
         */
        HAS_TYPE,

        /**
         * The use site (method) returns instances the used class.
         */
        RETURN_TYPE,

        /**
         * One of the parameters of the use site (method) has the type
         * of the used class.
         */
        PARAMETER_TYPE,

        /**
         * The use site (method) throws exceptions of the type of the used
         * class.
         */
        IS_THROWN,

        /**
         * The used class contains the use site (inner class).
         */
        CONTAINS;

        public static EnumSet<Type> all() {
            return EnumSet.allOf(UseSite.Type.class);
        }

        public static EnumSet<Type> allBut(UseSite.Type... types) {
            EnumSet<Type> ret = all();
            ret.removeAll(Arrays.asList(types));
            return ret;
        }
    }

    /**
     * A visitor of the use site.
     *
     * @param <R> the type of the returned value
     * @param <P> the type of the parameter passed to the visitor
     */
    public interface Visitor<R, P> {

        /**
         * Visits the use site.
         *
         * @param type      the type that is being used
         * @param use       the site of the use of the type
         * @param parameter the parameter passed by the caller
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

        return site.equals(useSite.site) && useType == useSite.useType;
    }

    @Override
    public int hashCode() {
        int result = useType.hashCode();
        result = 31 * result + site.hashCode();
        return result;
    }
}
