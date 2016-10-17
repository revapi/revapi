package org.revapi.java.model;

import javax.annotation.Nonnull;
import javax.lang.model.type.DeclaredType;

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

    @Override public javax.lang.model.element.TypeElement getDeclaringElement() {
        return element;
    }

    @Override public DeclaredType getModelRepresentation() {
        return (DeclaredType) element.asType();
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

        return getBinaryName().compareTo(((MissingClassElement) o).getBinaryName());
    }

}
