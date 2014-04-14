package org.revapi.java.spi;

import javax.annotation.Nonnull;
import javax.lang.model.element.VariableElement;

/**
 * Elements in the element forest that represent Java method parameters, will implement this interface.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface JavaMethodParameterElement extends JavaModelElement {
    @Nonnull
    @Override
    VariableElement getModelElement();
}
