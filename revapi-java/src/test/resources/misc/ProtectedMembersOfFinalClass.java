/*
 * Copyright 2014-2021 Lukas Krejci
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
public class ProtectedMembersOfFinalClass {

    public static final class Final {
        protected int field;
        protected void method() {}
    }

    public static class Base<T> {
        protected T protectedMethod() {
            return null;
        }
    }

    public static final class Inherited extends Base<Inherited.X> {
        enum X {
        }
    }
}