package org.revapi.java.checks.fields;

import java.util.EnumSet;

import javax.annotation.Nullable;

import org.revapi.java.checks.common.MovedInHierarchy;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaFieldElement;

/**
 * @author Lukas Krejci
 * @since 0.11.0
 */
public final class FieldMovedInHierarchy extends MovedInHierarchy {
    public FieldMovedInHierarchy() {
        super(Code.FIELD_MOVED_TO_SUPER_CLASS, Code.FIELD_INHERITED_NOW_DECLARED);
    }

    @Override public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.FIELD);
    }

    @Override protected void doVisitField(@Nullable JavaFieldElement oldField, @Nullable JavaFieldElement newField) {
        doVisit(oldField, newField);
    }
}
