/*
 * Copyright 2014 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.revapi.java.checks;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;

import org.revapi.Difference;
import org.revapi.java.model.ClassTreeInitializer;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.TypeEnvironment;

/**
 * TODO move to SPI
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public abstract class AbstractJavaCheck extends CheckBase {

    protected Difference createDifference(Code code,
        Object... params) {

        return createDifference(code, params, params);
    }

    protected Difference createDifference(Code code, Object[] params, Object... attachments) {
        return code.createDifference(getAnalysisContext().getLocale(), params, attachments);
    }

    protected static boolean isBothPrivate(Element a, Element b) {
        if (a == null || b == null) {
            return false;
        }

        return !ClassTreeInitializer.isAccessible(a) && !ClassTreeInitializer.isAccessible(b);
    }

    protected static boolean isBothAccessible(Element a, Element b) {
        if (a == null || b == null) {
            return false;
        }

        return ClassTreeInitializer.isAccessible(a) && ClassTreeInitializer.isAccessible(b);
    }

    protected static boolean isAccessible(Element e) {
        return ClassTreeInitializer.isAccessible(e);
    }

    protected static boolean isMissing(Element e) {
        return e.asType().getKind() == TypeKind.ERROR;
    }

    protected static boolean isAccessibleOrInAPI(Element e, TypeEnvironment env) {
        return isAccessible(e) || (e instanceof TypeElement && env.isExplicitPartOfAPI((TypeElement) e));
    }

    protected static boolean isBothAccessibleOrInApi(Element a, TypeEnvironment envA, Element b, TypeEnvironment envB) {
        return isAccessibleOrInAPI(a, envA) && isAccessibleOrInAPI(b, envB);
    }
}
