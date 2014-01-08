/*
 * Copyright 2014 Lukas Krejci
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

package org.revapi.java.classes.misc;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class B extends A {
    int f1;
    TopLevelPrivate f2;
    String f3;

    @Deprecated
    int m1(int p1, float p2) {
        return 0;
    }

    public void m2() {

    }

    public static void sm1() {

    }

    {

    }

    static {

    }
}
