package org.revapi.java.spi;

import javax.annotation.Nonnull;
import javax.lang.model.element.VariableElement;

/**
 * Elements in the element forest that represent Java fields, will implement this interface.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface JavaFieldElement extends JavaModelElement {
    @Nonnull
    @Override
    VariableElement getModelElement();
}
