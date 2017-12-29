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
package element.matcher;

public class TypeParameters {

    public static class Base<T> {
        public T method() {
            return null;
        }
    }

    public static class ConcreteChild extends Base<Integer> {

    }

    public static class GenericChild<T extends String, U> extends Base<T> {

        public <V extends T> U genericMethod(V param) {
            return null;
        }
    }
}