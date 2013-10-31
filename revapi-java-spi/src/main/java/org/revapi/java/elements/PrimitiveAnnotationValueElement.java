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

import java.lang.reflect.Array;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class PrimitiveAnnotationValueElement
    extends AnnotationAttributeValueElement<PrimitiveAnnotationValueElement> {

    private final Object value;

    public PrimitiveAnnotationValueElement(String descriptor, Object value) {
        super(descriptor);
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    @Override
    protected int doCompare(PrimitiveAnnotationValueElement that) {
        return arrayAwareCompare(value, that.value);
    }

    @Override
    public void appendToString(StringBuilder bld) {
        if (value.getClass().isArray()) {
            bld.append("{");
            int len = Array.getLength(value);
            if (len > 0) {
                appendFormatted(Array.get(value, 0), bld);
            }
            for (int i = 1; i < len; ++i) {
                bld.append(", ");
                appendFormatted(Array.get(value, i), bld);
            }
            bld.append("}");
        } else {
            appendFormatted(value, bld);
        }
    }

    @SuppressWarnings("unchecked")
    private int arrayAwareCompare(Object obj1, Object obj2) {
        if (obj1 == null) {
            return obj2 == null ? 0 : -1;
        } else {
            if (obj2 == null) {
                return 1;
            } else {
                boolean obj1IsArray = obj1.getClass().isArray();
                boolean obj2IsArray = obj2.getClass().isArray();

                if (obj1IsArray) {
                    if (!obj2IsArray) {
                        return 1;
                    }
                } else {
                    if (obj2IsArray) {
                        return -1;
                    }
                }

                if (!obj1IsArray) {
                    //primitive values
                    //we can only have numbers, chars or strings here, classes are handled differently
                    //so we should be able to safely cast to comparable
                    return ((Comparable<Object>) obj1).compareTo(obj2);
                } else {
                    //arrays
                    int len1 = Array.getLength(obj1);
                    int len2 = Array.getLength(obj2);

                    if (len1 != len2) {
                        return len1 - len2;
                    } else {
                        for (int i = 0; i < len1; ++i) {
                            Object o1 = Array.get(obj1, i);
                            Object o2 = Array.get(obj2, i);

                            int comp = arrayAwareCompare(o1, o2);
                            if (comp != 0) {
                                return comp;
                            }
                        }

                        return 0;
                    }
                }
            }
        }
    }

    private void appendFormatted(Object value, StringBuilder bld) {
        if (value instanceof String) {
            bld.append("\"").append(value).append("\"");
        } else {
            bld.append(value);
        }
    }
}
