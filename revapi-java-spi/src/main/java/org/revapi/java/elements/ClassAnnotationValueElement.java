/*
 * Copyright 2013 Lukas Krejci
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

package org.revapi.java.elements;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class ClassAnnotationValueElement extends AnnotationAttributeValueElement<ClassAnnotationValueElement> {
    private final String className;

    public ClassAnnotationValueElement(String descriptor, String className) {
        super(descriptor);
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    @Override
    protected int doCompare(ClassAnnotationValueElement that) {
        return className.compareTo(that.className);
    }

    @Override
    public void appendToString(StringBuilder bld) {
        bld.append(className);
    }
}
