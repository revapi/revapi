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

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import org.revapi.java.util.ClassUtil;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class MethodElement extends AccessibleElement<MethodElement> {

    public enum Modifier {
        STATIC, FINAL, SYNCHRONIZED, BRIDGE, NATIVE, ABSTRACT, STRICT, SYNTHETIC
    }

    private final String name;
    private final String returnType;
    private String[] exceptionTypes;
    private EnumSet<Modifier> modifiers;

    public MethodElement(String returnType, String name, String descriptor, String genericSignature,
        Access access) {
        super(descriptor, genericSignature, access);
        this.name = name;
        this.returnType = returnType;
    }

    public EnumSet<Modifier> getModifiers() {
        return modifiers;
    }

    public void setModifiers(EnumSet<Modifier> modifiers) {
        this.modifiers = modifiers;
    }

    public String getName() {
        return name;
    }

    public String getReturnType() {
        return returnType;
    }

    public String[] getExceptionTypes() {
        return exceptionTypes;
    }

    public void setExceptionTypes(String[] exceptionTypes) {
        this.exceptionTypes = exceptionTypes;
    }

    public void appendToString(StringBuilder bld) {
        List<AnnotationElement> annotations = getDirectChildrenOfType(AnnotationElement.class);
        for (AnnotationElement a : annotations) {
            a.appendToString(bld);
            bld.append("\n");
        }

        String access = getAccess().toString();
        if (access.length() > 0) {
            bld.append(access).append(" ");
        }

        if (modifiers != null && !modifiers.isEmpty()) {
            for (Modifier m : modifiers) {
                bld.append(m.name().toLowerCase()).append(" ");
            }
        }

        bld.append(getReturnType()).append(" ").append(name);

        bld.append("(");

        List<MethodParameterElement> pars = getDirectChildrenOfType(MethodParameterElement.class);
        if (!pars.isEmpty()) {
            Iterator<MethodParameterElement> it = pars.iterator();

            it.next().appendToString(bld);
            while (it.hasNext()) {
                bld.append(", ");
                it.next().appendToString(bld);
            }
        }

        bld.append(")");

        List<AnnotationAttributeValueElement> defaultValue = getDirectChildrenOfType(
            AnnotationAttributeValueElement.class);
        if (defaultValue.size() > 0) {
            bld.append(" default ");
            defaultValue.get(0).appendToString(bld);
        }

        if (exceptionTypes != null && exceptionTypes.length > 0) {
            bld.append(" throws ");
            bld.append(exceptionTypes[0]);

            for (int i = 1; i < exceptionTypes.length; ++i) {
                bld.append(", ").append(exceptionTypes[i]);
            }
        }

        bld.append(" {}");
    }

    public AnnotationAttributeValueElement<?> getAnnotationMethodDefaultValue() {
        Iterator<AnnotationAttributeElement> it = iterateOverChildren(AnnotationAttributeElement.class, false, null);

        return it.hasNext() ? it.next()
            .getValue(ClassUtil.<AnnotationAttributeValueElement<?>>generify(AnnotationAttributeValueElement.class)) :
            null;
    }

    @Override
    protected int doCompare(MethodElement that) {
        return name.compareTo(that.name);
    }
}
