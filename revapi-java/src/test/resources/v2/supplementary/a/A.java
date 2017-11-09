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
public class A {
    private B.T$1 f1;
    public B.T$2 f2;
    public B.T$3 f3;

    //this tests that a public field or method of a private class doesn't move it to API.
    private enum PrivateEnum {ONE, TWO}

    //this tests missing classes can be used as throws declarations, too
    public void m() throws B.T$2 {

    }
}
