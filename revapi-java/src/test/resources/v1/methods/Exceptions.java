/*
 * Copyright 2015 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

import java.io.IOException;

public class Exceptions {

    public void methodChecked1() throws Exception {}

    public void methodChecked2() throws IOException {}

    public void methodChecked3() throws Throwable {}

    public void methodRuntime1() throws RuntimeException {}

    public void methodRuntime2() throws IllegalArgumentException {}

    public void methodRuntime3() throws Error {}

    public void methodRuntime4() throws AssertionError {}
}
