package org.revapi.java.spi;

import javax.annotation.Nonnull;
import javax.lang.model.element.ExecutableElement;

/**
 * Elements in the element forest that represent Java methods, will implement this interface.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface JavaMethodElement extends JavaModelElement {
    @Nonnull
    @Override
    ExecutableElement getModelElement();
}
