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

import java.util.Iterator;
import java.util.List;

import org.revapi.Element;
import org.revapi.java.util.ClassUtil;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class AnnotationAttributeElement extends AbstractJavaElement<AnnotationAttributeElement> {

    private final String name;

    public AnnotationAttributeElement(String name) {
        this.name = name == null ? "value" : name;
    }

    public String getName() {
        return name;
    }

    public <T extends Element> T getValue(Class<T> valueType) {
        Iterator<T> it = this.iterateOverChildren(valueType, false, null);

        return it.hasNext() ? it.next() : null;
    }

    @Override
    public void appendToString(StringBuilder bld) {
        bld.append(getName()).append(" = ");

        @SuppressWarnings("rawtypes")
        List<AnnotationAttributeValueElement> values = getDirectChildrenOfType(AnnotationAttributeValueElement.class);

        if (values.isEmpty()) {
            //XXX should this be an error?
            bld.append("<no value specified>");
        } else if (values.size() == 1) {
            values.get(0).appendToString(bld);
        } else {
            //this is an error
        }
    }

    @Override
    protected int doCompare(AnnotationAttributeElement that) {
        //int comp = name == null ? (that.name == null ? 0 : -1) : name.compareTo(that.name);
        int comp = name.compareTo(that.name);
        if (comp != 0) {
            return comp;
        }

        //we need to compare based on value
        List<AnnotationAttributeValueElement<?>> vals = getDirectChildrenOfType(
            ClassUtil.<AnnotationAttributeValueElement<?>>generify(AnnotationAttributeValueElement.class));
        List<AnnotationAttributeValueElement<?>> thatVals = that.getDirectChildrenOfType(
            ClassUtil.<AnnotationAttributeValueElement<?>>generify(AnnotationAttributeValueElement.class));

        comp = vals.size() - thatVals.size();
        if (comp != 0 || vals.isEmpty()) {
            return comp;
        }

        //an annotation attribute should only ever have a single value
        AnnotationAttributeValueElement<?> thisVal = vals.get(0);
        AnnotationAttributeValueElement<?> thatVal = thatVals.get(0);

        return thisVal.compareTo(thatVal);
    }
}
