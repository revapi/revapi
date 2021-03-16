/*
 * Copyright 2014-2021 Lukas Krejci
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.revapi.java.checks.common;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * 
 * @since 0.11.0
 */
public abstract class MovedInHierarchy extends CheckBase {
    private final Code moveUp;
    private final Code moveDown;

    protected MovedInHierarchy(Code moveUp, Code moveDown) {
        this.moveUp = moveUp;
        this.moveDown = moveDown;
    }

    protected void doVisit(@Nullable JavaModelElement oldEl, @Nullable JavaModelElement newEl) {
        if (!isBothAccessible(oldEl, newEl)) {
            return;
        }

        assert oldEl != null;
        assert newEl != null;

        if (oldEl.isInherited() == newEl.isInherited()) {
            return;
        }

        String oldType = Util.toUniqueString(oldEl.getModelRepresentation());
        String newType = Util.toUniqueString(newEl.getModelRepresentation());

        if (oldType.equals(newType)) {
            pushActive(oldEl, newEl);
        }
    }

    @Nullable
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaModelElement> els = popIfActive();
        if (els == null) {
            return null;
        }

        String oldType = Util
                .toHumanReadableString(els.oldElement.getDeclaringElement().getEnclosingElement().asType());
        String newType = Util
                .toHumanReadableString(els.newElement.getDeclaringElement().getEnclosingElement().asType());

        // we know that oldEl.isInherited() != newEl.isInherited(), so it's enough to just check for the old
        Code code = els.oldElement.isInherited() ? moveDown : moveUp;

        return Collections.singletonList(createDifference(code,
                Code.attachmentsFor(els.oldElement, els.newElement, "oldClass", oldType, "newClass", newType)));
    }
}
