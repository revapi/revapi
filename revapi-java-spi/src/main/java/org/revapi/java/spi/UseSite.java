package org.revapi.java.spi;

import javax.annotation.Nonnull;

import org.revapi.Element;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class UseSite {
    public enum Type {
        ANNOTATES, IS_INHERITED, IS_IMPLEMENTED, HAS_TYPE, RETURN_TYPE, PARAMETER_TYPE, IS_THROWN
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
