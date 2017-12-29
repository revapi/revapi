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

public class Overrides {

    public static class Base {

        public Number baseMethod(int i) {
            return null;
        }
    }

    public interface Iface {

        void ifaceMethod(String s);
    }

    public static class Check extends Base implements Iface {

        public Integer baseMethod(int i) {
            return null;
        }

        public void ifaceMethod(String s) {

        }

        public void myMethod() {

        }
    }
}