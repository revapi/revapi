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

import org.revapi.java.util.ClassUtil;
import org.revapi.query.Filter;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class AnnotationElement extends AnnotationAttributeValueElement<AnnotationElement> {
    public AnnotationElement(String className, String descriptor) {
        super(descriptor);
        this.className = className;
    }

    private final String className;

    public final String getClassName() {
        return className;
    }

    public AnnotationAttributeValueElement<?> getAttribute(final String name) {
        List<AnnotationAttributeElement> attrs = searchChildren(AnnotationAttributeElement.class, false,
            new Filter<AnnotationAttributeElement>() {

                @Override
                public boolean applies(AnnotationAttributeElement object) {
                    return name.equals(object.getName());
                }

                @Override
                public boolean shouldDescendInto(Object object) {
                    return this == object;
                }
            });

        if (attrs.isEmpty()) {
            return null;
        } else {
            return attrs.get(0).getValue(
                ClassUtil.<AnnotationAttributeValueElement<?>>generify(AnnotationAttributeValueElement.class));
        }
    }

    public void appendToString(StringBuilder bld) {
        bld.append("@");
        bld.append(getClassName());
        bld.append("(");

        List<AnnotationAttributeElement> attrs = getDirectChildrenOfType(AnnotationAttributeElement.class);

        if (attrs.size() > 0) {
            Iterator<AnnotationAttributeElement> it = attrs.iterator();

            AnnotationAttributeElement a = it.next();
            a.appendToString(bld);
            while (it.hasNext()) {
                bld.append(", ");
                a = it.next();
                a.appendToString(bld);
            }
        }

        bld.append(")");
    }

    @Override
    protected int doCompare(AnnotationElement that) {
        int ret = getClassName().compareTo(that.getClassName());
        if (ret != 0) {
            return ret;
        }

        List<AnnotationAttributeElement> thisAttrs = getDirectChildrenOfType(AnnotationAttributeElement.class);
        List<AnnotationAttributeElement> thatAttrs = getDirectChildrenOfType(AnnotationAttributeElement.class);

        int comp = thisAttrs.size() - thatAttrs.size();
        if (comp != 0 || thisAttrs.isEmpty()) {
            return comp;
        }

        Iterator<AnnotationAttributeElement> thisIt = thisAttrs.iterator();
        Iterator<AnnotationAttributeElement> thatIt = thatAttrs.iterator();

        while (thisIt.hasNext()) {
            AnnotationAttributeElement thisAttr = thisIt.next();
            AnnotationAttributeElement thatAttr = thatIt.next();

            comp = thisAttr.compareTo(thatAttr);
            if (comp != 0) {
                return comp;
            }
        }

        return 0;
    }
}
