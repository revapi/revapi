/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.revapi.java.checks;

import javax.annotation.Nonnull;
import javax.lang.model.element.Element;

import org.jboss.dmr.ModelNode;
import org.revapi.AnalysisContext;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.TypeEnvironment;

/**
 * @author Lukas Krejci
 * @since 0.5.3
 */
public abstract class ConfigurationAwareCheckBase extends CheckBase {

    private boolean skipUseTracking;

    public boolean isSkipUseTracking() {
        return skipUseTracking;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        super.initialize(analysisContext);
        ModelNode node = analysisContext.getConfiguration().get("revapi", "java", "deepUseChainAnalysis");
        skipUseTracking = !node.isDefined() || !node.asBoolean();
    }

    @Override
    public boolean isAccessibleOrInAPI(@Nonnull Element e, @Nonnull TypeEnvironment env) {
        return skipUseTracking ? isAccessible(e) : super.isAccessibleOrInAPI(e, env);
    }

    @Override
    public boolean isBothAccessibleOrInApi(@Nonnull Element a, @Nonnull TypeEnvironment envA, @Nonnull Element b,
                                           @Nonnull TypeEnvironment envB) {
        return skipUseTracking ? isBothAccessible(a, b) : super.isBothAccessibleOrInApi(a, envA, b, envB);
    }
}
