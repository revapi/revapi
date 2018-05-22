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

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;

import javax.lang.model.element.TypeElement;

import org.revapi.Difference;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaTypeElement;

/**
 * @author Lukas Krejci
 * @author James Phillpotts, ForgeRock AS.
 * @since 0.1
 */
public final class Added extends InternalTypeWhitelistCheckBase {
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaTypeElement> types = popIfActive();
        if (types != null) {
            TypeElement typeInOld = getOldTypeEnvironment().getElementUtils()
                    .getTypeElement(types.newElement.getDeclaringElement().getQualifiedName());

            LinkedHashMap<String, String> attachments = Code.attachmentsFor(types.oldElement, types.newElement);
            if (typeInOld == null) {
                return Collections.singletonList(createDifference(Code.CLASS_ADDED, attachments));
            } else if (!isInternalType(typeInOld.toString())) {
                return Collections.singletonList(createDifference(Code.CLASS_EXTERNAL_CLASS_EXPOSED_IN_API, attachments));
            }
        }

        return null;
    }

    @Override
    public String getExtensionId() {
        return "externalClassExposedInAPI";
    }

    @Override
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("/META-INF/externalClassExposedInAPI-config-schema.json"),
                Charset.forName("UTF-8"));
    }

    @Override
    protected void doVisitClass(JavaTypeElement oldType, JavaTypeElement newType) {
        if (oldType == null && newType != null && isAccessible(newType)) {
            pushActive(null, newType);
        }
    }

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.CLASS);
    }
}
