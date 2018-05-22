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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.jboss.dmr.ModelNode;
import org.revapi.AnalysisContext;
import org.revapi.java.spi.CheckBase;

/**
 * An extension of {@link CheckBase} that can be configured with a whitelist for identifying
 * types that are considered internal even though they are not in the accessible source.
 *
 * @author James Phillpotts, ForgeRock AS.
 */
abstract class InternalTypeWhitelistCheckBase extends CheckBase {
    private List<Predicate<String>> internalTypes;

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        super.initialize(analysisContext);
        ModelNode internalTypes = analysisContext.getConfiguration().get("internalTypes");
        if (internalTypes.isDefined()) {
            List<Predicate<String>> typePatterns = new ArrayList<>();
            for (ModelNode pattern : internalTypes.asList()) {
                typePatterns.add(Pattern.compile(pattern.asString()).asPredicate());
            }
            this.internalTypes = typePatterns;
        } else {
            this.internalTypes = new ArrayList<>();
        }
    }

    /**
     * Checks whether the provided type is an internal type from the whitelist.
     *
     * @param type The fully qualified type name.
     * @return {@literal true} if the type is whitelisted.
     */
    protected boolean isInternalType(String type) {
        for (Predicate<String> p : internalTypes) {
            if (p.test(type)) {
                return true;
            }
        }
        return false;
    }
}
