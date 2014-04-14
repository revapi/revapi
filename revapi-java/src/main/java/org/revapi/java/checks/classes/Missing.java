package org.revapi.java.checks.classes;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.lang.model.element.TypeElement;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Missing extends CheckBase {

    @Override
    protected void doVisitClass(@Nullable TypeElement oldType, @Nullable TypeElement newType) {
        if ((oldType != null && isMissing(oldType)) || (newType != null && isMissing(newType))) {
            pushActive(oldType, newType);
        }
    }

    @Nullable
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<TypeElement> types = popIfActive();
        if (types == null) {
            return null;
        }

        List<Difference> ret = new ArrayList<>();

        if (types.oldElement != null) {
            ret.add(createDifference(Code.MISSING_IN_OLD_API, types.oldElement.getQualifiedName().toString()));
        }

        if (types.newElement != null) {
            ret.add(createDifference(Code.MISSING_IN_NEW_API, types.newElement.getQualifiedName().toString()));
        }

        return ret;
    }
}
