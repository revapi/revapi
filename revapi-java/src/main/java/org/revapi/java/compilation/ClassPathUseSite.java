package org.revapi.java.compilation;

import javax.lang.model.element.Element;

import org.revapi.java.spi.UseSite;

/**
 * @author Lukas Krejci
 * @since 0.11.0
 */
public final class ClassPathUseSite {
    public final UseSite.Type useType;
    public final Element site;
    public final int indexInParent;

    ClassPathUseSite(UseSite.Type useType, Element site, int indexInParent) {
        this.useType = useType;
        this.site = site;
        this.indexInParent = indexInParent;
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClassPathUseSite that = (ClassPathUseSite) o;

        if (useType != that.useType) {
            return false;
        }

        return site.equals(that.site);

    }

    @Override public int hashCode() {
        int result = useType.hashCode();
        result = 31 * result + site.hashCode();
        return result;
    }
}
