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
import java.io.IOException;

public class ExceptionsAndInheritance {

    private static abstract class Base {
        public abstract void abstractChecked() throws IOException;
        public abstract void abstractUnchecked();

        public void concreteChecked() throws IOException {
            throw new IOException();
        }
        public void concreteUnchecked() throws IllegalArgumentException {
        }
    }

    public static class ChildWithNoExceptions extends Base {
        public void abstractChecked() {}
        public void abstractUnchecked() throws IllegalArgumentException {}
    }

    public static class ChildWithSpecializedExceptions extends Base {
        public void abstractChecked() throws java.io.FileNotFoundException {
            throw new java.io.FileNotFoundException();
        }

        public void abstractUnchecked() {}
    }
}
