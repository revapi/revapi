package org.revapi.java.checks.methods;

import java.util.EnumSet;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;

import org.revapi.java.checks.common.ModifierChanged;
import org.revapi.java.spi.Code;

/**
 * @author Lukas Krejci
 * @since 0.2
 */
public final class NoLongerStatic extends ModifierChanged {
    public NoLongerStatic() {
        super(false, Code.METHOD_NO_LONGER_STATIC, Modifier.STATIC);
    }

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.METHOD);
    }

    @Override
    protected void doVisitMethod(@Nullable ExecutableElement oldMethod, @Nullable ExecutableElement newMethod) {
        doVisit(oldMethod, newMethod);
    }
}
