package org.revapi.java.spi;

import javax.annotation.Nonnull;
import javax.lang.model.element.TypeElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public interface JavaTypeElement extends JavaModelElement {

    @Nonnull
    @Override
    TypeElement getModelElement();
}
