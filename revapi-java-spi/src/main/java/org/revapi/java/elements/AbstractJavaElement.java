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

import org.revapi.Element;
import org.revapi.java.Java;
import org.revapi.simple.SimpleElement;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
abstract class AbstractJavaElement<This extends AbstractJavaElement<This>> extends SimpleElement<Java> {
    private final JavaElementType myType;

    protected AbstractJavaElement() {
        myType = JavaElementType.of(this);
    }

    public JavaElementType getJavaElementType() {
        return myType;
    }

    @Override
    public final int compareTo(Element o) {
        if (!(o instanceof AbstractJavaElement)) {
            return -1;
        }

        @SuppressWarnings("unchecked") This that = (This) o;

        int ordinalDiff = myType.ordinal() - that.getJavaElementType().ordinal();

        return ordinalDiff == 0 ? doCompare(that) : ordinalDiff;
    }

    protected abstract int doCompare(This that);

    public abstract void appendToString(StringBuilder bld);

    @Override
    public final String toString() {
        StringBuilder bld = new StringBuilder();

        appendToString(bld);

        return bld.toString();
    }
}
