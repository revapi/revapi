/*
 * Copyright 2014-2017 Lukas Krejci
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
public class B {
    /**
     * This changed from class to interface, but if the class C, which uses this, is ignored, we should get no
     * problem reported.
     */
    public static interface UsedByIgnoredClass {}

    public static class T$1 {
        /** this change should be ignored, because it is not "visible" from the API defined by A. */
        private static interface TT$1 {
            public static final T$2 f = null;
        }

        static class Private {}
    };

    /**
     * this change though, should be detected, because T2 is technically part of the API, because it is leaked into
     * the API as a type of public field in class A.
     */
    public static final class T$2 extends Exception {
        public T$1 f;
        public T$1.Private f2;
    };

    private static class PrivateUsedClass {

    }

    private static class PrivateBase {

        /**
         * This will be reported as a usage of private class in a public capacity, because T$3 is in the API and
         * inherits this public method.
         */
        public PrivateUsedClass getThat() {
            return null;
        }
    }

    private static class PrivateSuperClass extends PrivateBase {
    }

    /**
     * This will NOT be reported as a usage of a private class in a public capacity. Inheriting from a
     * private class by a public API class is a valid design pattern.
     */
    public static final class T$3 extends PrivateSuperClass  {

    }
}
