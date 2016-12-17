package org.revapi.java.checks.classes;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaTypeElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Missing extends CheckBase {

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.CLASS);
    }

    @Override
    protected void doVisitClass(@Nullable JavaTypeElement oldType, @Nullable JavaTypeElement newType) {
        boolean oldMissing = oldType != null && isMissing(oldType.getDeclaringElement());
        boolean oldInApi = oldType != null && oldType.isInAPI();
        boolean newMissing = newType != null && isMissing(newType.getDeclaringElement());
        boolean newInApi = newType != null && newType.isInAPI();

        if ((oldMissing || newMissing) && (oldInApi || newInApi)) {
            pushActive(oldType, newType);
        }
    }

    @Nullable
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaTypeElement> types = popIfActive();
        if (types == null) {
            return null;
        }

        List<Difference> ret = new ArrayList<>();

        if (types.oldElement != null) {
            ret.add(createDifferenceWithExplicitParams(Code.MISSING_IN_OLD_API,
                    Code.attachmentsFor(types.oldElement, types.newElement),
                    types.oldElement.getDeclaringElement().getQualifiedName().toString()));
        }

        if (types.newElement != null) {
            ret.add(createDifferenceWithExplicitParams(Code.MISSING_IN_NEW_API,
                    Code.attachmentsFor(types.oldElement, types.newElement),
                    types.newElement.getDeclaringElement().getQualifiedName().toString()));
        }

        return ret;
    }
}
