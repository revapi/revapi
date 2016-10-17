package org.revapi.java.checks.methods;

import java.util.EnumSet;

import javax.annotation.Nullable;

import org.revapi.java.checks.common.MovedInHierarchy;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaMethodElement;

/**
 * @author Lukas Krejci
 * @since 0.11.0
 */
public final class MethodMovedInHierarchy extends MovedInHierarchy {
    public MethodMovedInHierarchy() {
        super(Code.METHOD_MOVED_TO_SUPERCLASS, Code.METHOD_INHERITED_METHOD_MOVED_TO_CLASS);
    }

    @Override public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.METHOD);
    }

    @Override
    protected void doVisitMethod(@Nullable JavaMethodElement oldMethod, @Nullable JavaMethodElement newMethod) {
        doVisit(oldMethod, newMethod);
    }
}
