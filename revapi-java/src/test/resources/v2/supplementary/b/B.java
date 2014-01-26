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

public class B {
    /** this change should be ignored, because it is not "visible" from the API defined by A. */
    public static interface T$1 {
        public static class TT$1 {
        }
    };

    /**
     * this change though, should be detected, because T2 is technically part of the API, because it is leaked into
     * the API as a type of public field in class A.
     */
    public static final class T$2 {};

}
