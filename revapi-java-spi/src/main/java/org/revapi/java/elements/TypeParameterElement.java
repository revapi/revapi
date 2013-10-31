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
public final class TypeParameterElement extends AbstractJavaElement<TypeParameterElement> {
    @Override
    protected int doCompare(TypeParameterElement that) {
        return 0;  //TODO implement
    }

    @Override
    public void appendToString(StringBuilder bld) {
        //TODO implement
    }
}
