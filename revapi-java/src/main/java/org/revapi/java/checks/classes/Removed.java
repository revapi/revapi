/*
 * Copyright 2014-2018 Lukas Krejci
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
package org.revapi.java.checks.classes;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaTypeElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Removed extends CheckBase {
    private Set<Archive> primaryApi;

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        super.initialize(analysisContext);

        // the primary API most often consists of a single archive. Let's reserve capacity for 2 just to be sure, but
        // don't waste space just because of the unlikely possibility of more primary archives.
        primaryApi = new HashSet<>(2);
        analysisContext.getOldApi().getArchives().forEach(primaryApi::add);
    }

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.CLASS);
    }

    @Override
    protected void doVisitClass(JavaTypeElement oldType, JavaTypeElement newType) {
        if (oldType != null && newType == null && isAccessible(oldType)) {
            pushActive(oldType, null);
        }
    }

    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaTypeElement> types = popIfActive();
        if (types != null) {
            Difference difference = primaryApi.contains(types.oldElement.getArchive())
                    ? createDifference(Code.CLASS_REMOVED, Code.attachmentsFor(types.oldElement, types.newElement))
                    : createDifference(Code.CLASS_EXTERNAL_CLASS_NO_LONGER_EXPOSED_IN_API,
                            Code.attachmentsFor(types.oldElement, types.newElement));

            return Collections.singletonList(difference);
        }

        return null;
    }
}
