package org.revapi.java.spi;

import javax.annotation.Nonnull;
import javax.lang.model.element.VariableElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public interface JavaFieldElement extends JavaModelElement {
    @Nonnull
    @Override
    VariableElement getModelElement();
}
