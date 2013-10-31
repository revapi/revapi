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
import java.util.List;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class ClassElement extends AccessibleElement<ClassElement> {

    public enum Modifier {
        FINAL, INTERFACE, ABSTRACT,
        SYNTHETIC, ANNOTATION, ENUM, DEPRECATED;
    }

    private int classVersion;
    private EnumSet<Modifier> modifiers;
    private String superClass;
    private String[] interfaces;
    private final String className;

    public ClassElement(String className, String descriptor, String genericSignature, Access access) {
        super(descriptor, genericSignature, access);
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    public int getClassVersion() {
        return classVersion;
    }

    public void setClassVersion(int classVersion) {
        this.classVersion = classVersion;
    }

    public String[] getInterfaces() {
        return interfaces;
    }

    public void setInterfaces(String[] interfaces) {
        this.interfaces = interfaces;
    }

    public EnumSet<Modifier> getModifiers() {
        return modifiers;
    }

    public void setModifiers(EnumSet<Modifier> modifiers) {
        this.modifiers = modifiers;
    }

    public String getSuperClass() {
        return superClass;
    }

    public void setSuperClass(String superClass) {
        this.superClass = superClass;
    }

    @Override
    public void appendToString(StringBuilder bld) {
        List<AnnotationElement> annotations = getDirectChildrenOfType(AnnotationElement.class);
        for (AnnotationElement a : annotations) {
            a.appendToString(bld);
            bld.append("\n");
        }

        bld.append(getAccess()).append(" ");
        if (modifiers != null && !modifiers.isEmpty()) {
            for (Modifier m : modifiers) {
                bld.append(m.name().toLowerCase()).append(" ");
            }
        }

        bld.append(getClassName());
        if (getSuperClass() != null) {
            bld.append(" extends ").append(getSuperClass());
        }

        String[] ifaces = getInterfaces();
        if (ifaces != null && ifaces.length > 0) {
            bld.append(" implements ");
            bld.append(ifaces[0]);

            for (int i = 1; i < ifaces.length; ++i) {
                bld.append(", ").append(ifaces[i]);
            }
        }

        bld.append(" {\n");

        List<FieldElement> fields = getDirectChildrenOfType(FieldElement.class);
        for (FieldElement f : fields) {
            bld.append("    ");
            f.appendToString(bld);
            bld.append("\n");
        }

        List<MethodElement> methods = getDirectChildrenOfType(MethodElement.class);
        for (MethodElement m : methods) {
            bld.append("    ");
            m.appendToString(bld);
            bld.append("\n");
        }

        bld.append("}");
    }

    @Override
    protected int doCompare(ClassElement that) {
        return getClassName().compareTo(that.getClassName());
    }
}
