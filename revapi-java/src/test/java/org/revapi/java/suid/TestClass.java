/*
 * Copyright 2014-2023 Lukas Krejci
 * and other contributors as indicated by the @author tags.
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
 * limitations under the License.
 */
package org.revapi.java.suid;

import java.io.Serializable;

/**
 * Needs to be kept in exact sync with the src/test/resources/org/revapi/java/suid/TestClass.java
 *
 * @author Lukas Krejci
 *
 * @since 0.1
 */
@SuppressWarnings("don't care")
public class TestClass implements Serializable {
    private static int sf1;
    protected static float sf2;
    static double sf3;
    public static String sf4;

    private int f1;
    protected float f2;
    double f3;
    public String f4;

    private transient int tf1;

    private TestClass() {

    }

    TestClass(int i) {

    }

    protected TestClass(float f) {

    }

    public TestClass(double d) {

    }

    private static void svm1(int i) {

    }

    static void svm2(int i) {

    }

    public static void svm3(int i) {

    }

    private void vm1(int i) {

    }

    void vm2(int i) {

    }

    public void vm3(int i) {

    }

    private static String srm1(int i) {
        return null;
    }

    static String srm2(int i) {
        return null;
    }

    public static String srm3(int i) {
        return null;
    }

    private String rm1(String i) {
        return null;
    }

    String rm2(int i) {
        return null;
    }

    public String rm3(int i) {
        return null;
    }
}
