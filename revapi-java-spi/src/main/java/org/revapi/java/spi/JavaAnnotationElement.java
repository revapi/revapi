/*
 * Copyright 2014 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi.java.spi;

import javax.annotation.Nonnull;
import javax.lang.model.element.AnnotationMirror;

/**
 * Elements in the element forest that correspond to a Java annotation will implement this interface.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface JavaAnnotationElement extends JavaElement {

    /**
     * @return the corresponding annotation mirror
     */
    @Nonnull
    AnnotationMirror getAnnotation();

    @Override
    @Nonnull
    JavaModelElement getParent();
}
