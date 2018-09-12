/*
 * Copyright 2014-2018 Lukas Krejci
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
public class Serialization {

    public static class DefaultSerial implements java.io.Serializable {
        public void method() {

        }

        public static class NonSerializable {
            private static final long serialVersionUID = 2; //decoy ;)
        }
    }

    public static class ExplicitSerial1 implements java.io.Serializable {
        public void method() {

        }
    }

    public static class ExplicitSerial2 implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        public void method() {

        }
    }
}
