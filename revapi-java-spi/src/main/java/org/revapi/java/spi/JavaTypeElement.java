/*
 * Copyright 2014-2023 Lukas Krejci
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
package org.revapi.java.spi;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

/**
 * Elements in the element forest that represent Java types, will implement this interface.
 *
 * @author Lukas Krejci
 *
 * @since 0.1
 */
public interface JavaTypeElement extends JavaModelElement {

    @Override
    DeclaredType getModelRepresentation();

    @Override
    TypeElement getDeclaringElement();

    /**
     * @return true if this type was found to be a part of the API, false otherwise
     */
    boolean isInAPI();

    /**
     * @return true, if the class is not accessible in and of itself but is dragged into the API by a significant use.
     */
    boolean isInApiThroughUse();
}
