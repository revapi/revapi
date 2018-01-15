package org.revapi.java.compilation;

import java.util.Objects;

import javax.lang.model.element.Element;

import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.UseSite;

/**
 * A use site that was caused by an inherited element. If a use site is inherited by another type (e.g. an inherited
 * method), the used type must also include a new use site expressing the inherited element. This class is used for
 * precisely that.
 */
public class InheritedUseSite extends ClassPathUseSite {
    public final TypeElement inheritor;

    InheritedUseSite(UseSite.Type useType, Element site, TypeElement inheritor, int indexInParent) {
        super(useType, site, indexInParent);
        this.inheritor = inheritor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        InheritedUseSite that = (InheritedUseSite) o;
        return Objects.equals(inheritor, that.inheritor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), inheritor);
    }
}
