package org.revapi.java.spi;

import java.util.Set;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

/**
 * Elements in the element forest that represent Java types, will implement this interface.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface JavaTypeElement extends JavaModelElement {

    @Override
    DeclaredType getModelRepresentation();

    @Override TypeElement getDeclaringElement();

    /**
     * @return the set of "places" where this type element is used
     */
    Set<UseSite> getUseSites();

    /**
     * Visits the uses of the provided type. The visit will stop as soon as a non-null value is returned
     * from the visitor, even if some use sites are left unvisited.
     *
     * @param <R>       the return type (use {@link java.lang.Void} for no return type)
     * @param <P>       the type of the parameter (use {@link java.lang.Void} for no particular type)
     * @param visitor   the visitor
     * @param parameter the parameter to supply to the visitor
     *
     * @return the value returned by the visitor
     */
    default <R, P> R visitUseSites(UseSite.Visitor<R, P> visitor, P parameter) {
        DeclaredType type = getModelRepresentation();
        for (UseSite u : getUseSites()) {
            R ret = visitor.visit(type, u, parameter);
            if (ret != null) {
                return ret;
            }
        }

        return visitor.end(type, parameter);
    }

    /**
     * @return true if this type was found to be a part of the API, false otherwise
     */
    boolean isInAPI();
}
