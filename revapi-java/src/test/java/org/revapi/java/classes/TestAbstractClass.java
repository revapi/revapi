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

package org.revapi.java.classes;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
@TestAnnotation(param6 = {})
public abstract class TestAbstractClass {

    private int field1;
    int field2;

    @TestAnnotation(param6 = {})
    public int field3;

    public abstract void method(int p1);

    public String method2(int p1, @TestAnnotation(param6 = {}) float p2, TestAbstractClass p3) {
        return null;
    }
}
