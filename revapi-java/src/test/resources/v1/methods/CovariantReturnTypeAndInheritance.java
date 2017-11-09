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
public class CovariantReturnTypeAndInheritance {

    private static class Base<T> {
        public Object method() {
            return null;
        }

        public T genericMethod() {
            return null;
        }

        public Number nonGenericMethod() {
            return null;
        }
    }

    public static class Class<E extends Number> extends Base<E> {
        public E genericMethod() {
            return null;
        }

        public E nonGenericMethod() {
            return null;
        }
    }
}
