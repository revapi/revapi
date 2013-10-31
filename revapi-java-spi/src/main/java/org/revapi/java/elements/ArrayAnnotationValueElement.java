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
import java.util.SortedSet;

import org.revapi.Element;
import org.revapi.java.util.InsertionOrderSortedSet;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class ArrayAnnotationValueElement extends AnnotationAttributeValueElement<ArrayAnnotationValueElement> {

    public ArrayAnnotationValueElement() {
        super(null);
    }

    @Override
    protected SortedSet<Element> newChildrenInstance() {
        return new InsertionOrderSortedSet<>();
    }

    public int getLength() {
        return searchChildren(AnnotationAttributeValueElement.class, false, null).size();
    }

    @SuppressWarnings("unchecked")
    public AnnotationAttributeValueElement<?> get(int index) {
        return searchChildren(
            (Class<AnnotationAttributeValueElement<?>>) (Class<?>) AnnotationAttributeValueElement.class, false, null)
            .get(index);
    }

    @Override
    protected int doCompare(ArrayAnnotationValueElement that) {
        int sizeDiff = getChildren().size() - that.getChildren().size();
        if (sizeDiff != 0) {
            return sizeDiff;
        }

        Iterator<Element> myIt = getChildren().iterator();
        Iterator<Element> thatIt = that.getChildren().iterator();

        while (myIt.hasNext()) {
            Element my = myIt.next();
            Element his = thatIt.next();

            int comp = my.compareTo(his);
            if (comp != 0) {
                return comp;
            }
        }

        return 0;
    }

    @Override
    public void appendToString(StringBuilder bld) {
        bld.append("[");
        if (getChildren().size() > 0) {
            Iterator<Element> it = getChildren().iterator();
            appendToString(it.next(), bld);

            while (it.hasNext()) {
                bld.append(", ");
                appendToString(it.next(), bld);
            }
        }
        bld.append("]");
    }

    private void appendToString(Element el, StringBuilder bld) {
        if (el instanceof AbstractJavaElement) {
            ((AbstractJavaElement<?>) el).appendToString(bld);
        } else {
            bld.append(el);
        }
    }
}
