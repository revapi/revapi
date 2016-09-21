package org.revapi.java.spi;

import java.util.Set;

import javax.annotation.Nonnull;
import javax.lang.model.element.TypeElement;

/**
 * Elements in the element forest that represent Java types, will implement this interface.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface JavaTypeElement extends JavaModelElement {

    @Nonnull
    @Override
    TypeElement getModelElement();


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
        TypeElement type = getModelElement();
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

    /**
     * If a member of a class is accessible depends on 2 things. First, it has to have a modifier that allows "public"
     * access (i.e. public or protected) but secondly the class that contains that member has to be accessible somehow.
     *
     * <p>For that the rules are somewhat more complicated:
     * The <b>public</b> members of the class are accessible iff:
     * <ol>
     * <li> they are not overridden by an accessible subclass
     * <li> the class is public (regardless of final) and all its enclosing classes are public or protected non-final,
     * <li> the class is protected (non-final) and all its enclosing classes are public or protected non-final,
     * <li> the class is not accessible but at least one of its (indirect) subclasses is accessible and the rules 1. and
     *      2. apply for that subclass.
     *
     * @return true if the above rules apply for this type, false otherwise
     */
    boolean isMembersAccessible();
}
