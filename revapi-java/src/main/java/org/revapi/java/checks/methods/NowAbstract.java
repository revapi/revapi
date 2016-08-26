package org.revapi.java.checks.methods;

import java.util.EnumSet;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;

import org.revapi.java.checks.common.ModifierChanged;
import org.revapi.java.spi.Code;

/**
 * @author Lukas Krejci
 * @since 0.11.0
 */
public final class NowAbstract extends ModifierChanged {
    public NowAbstract() {
        super(true, Code.METHOD_NOW_ABSTRACT, Modifier.ABSTRACT);
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
