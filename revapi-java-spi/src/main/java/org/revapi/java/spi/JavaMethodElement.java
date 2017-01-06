package org.revapi.java.spi;

import javax.annotation.Nonnull;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ExecutableType;

/**
 * Elements in the element forest that represent Java methods, will implement this interface.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface JavaMethodElement extends JavaModelElement {
    @Override
    ExecutableType getModelRepresentation();

    @Override
    ExecutableElement getDeclaringElement();

    @Override
    @Nonnull
    JavaTypeElement getParent();
}
