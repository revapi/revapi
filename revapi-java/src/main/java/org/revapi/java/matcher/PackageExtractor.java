/*
 * Copyright 2015-2017 Lukas Krejci
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
 *
 */

package org.revapi.java.matcher;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.type.TypeMirror;

import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
final class PackageExtractor implements DataExtractor<String> {
    @Override
    public String extract(JavaModelElement element) {
        return packageOf(element.getDeclaringElement());
    }

    @Override
    public String extract(JavaAnnotationElement element) {
        return packageOf(element.getAnnotation().getAnnotationType().asElement());
    }

    @Override
    public String extract(TypeMirror type) {
        return "";
    }

    @Override
    public Class<String> extractedType() {
        return String.class;
    }

    private String packageOf(Element element) {
        while (element != null && !(element instanceof PackageElement)) {
            element = element.getEnclosingElement();
        }

        return element == null ? "" : ((PackageElement) element).getQualifiedName().toString();
    }
}
