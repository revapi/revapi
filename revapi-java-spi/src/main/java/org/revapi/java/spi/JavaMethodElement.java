package org.revapi.java.spi;

import javax.annotation.Nonnull;
import javax.lang.model.element.ExecutableElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public interface JavaMethodElement extends JavaModelElement {
    @Nonnull
    @Override
    ExecutableElement getModelElement();
}
