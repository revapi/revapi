package org.revapi.java.model;

import javax.annotation.Nonnull;

import org.revapi.Element;
import org.revapi.java.compilation.ProbingEnvironment;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class MissingClassElement extends TypeElement {

    private final MissingTypeElement element;

    public MissingClassElement(ProbingEnvironment env, String binaryName, String canonicalName) {
        super(env, null, binaryName, canonicalName);
        element = new MissingTypeElement(canonicalName);
    }

    @Nonnull
    @Override
    public javax.lang.model.element.TypeElement getModelElement() {
        return element;
    }

    @Nonnull
    @Override
    protected String getHumanReadableElementType() {
        return "missing-class";
    }

    @Override
    public int compareTo(@Nonnull Element o) {
        if (!(o instanceof MissingClassElement)) {
            return JavaElementFactory.compareByType(this, o);
        }

        return super.compareTo(o);
    }

}
