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

import java.util.List;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class MethodParameterElement extends DescriptorElement<MethodParameterElement> {

    private final int position;
    private final String type;

    public MethodParameterElement(int position, String type, String descriptor, String genericSignature) {
        super(descriptor, genericSignature);
        this.position = position;
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public int getPosition() {
        return position;
    }

    public void appendToString(StringBuilder bld) {
        List<AnnotationElement> annotations = getDirectChildrenOfType(AnnotationElement.class);
        for (AnnotationElement a : annotations) {
            a.appendToString(bld);
            bld.append(" ");
        }

        bld.append(getType());
    }

    @Override
    protected int doCompare(MethodParameterElement that) {
        int comp = position - that.position;
        if (comp != 0) {
            return comp;
        }

        comp = getType().compareTo(that.getType());
        if (comp != 0) {
            return comp;
        }

        String sig = getGenericSignature();
        if (sig == null) {
            return that.getGenericSignature() == null ? 0 : -1;
        } else {
            return sig.compareTo(that.getGenericSignature());
        }
    }
}
