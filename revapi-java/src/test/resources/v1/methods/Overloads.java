/*
 * Copyright 2014-2019 Lukas Krejci
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
public interface Overloads {

    void a();

    int a(int i);

    void a(int i, long l);

    void a(int i, long l, double d);

    void a(int i, long l, double d, float f);

    void b(Class<? extends Integer> x, Object y);

    void b(Object y);

    void c(Class<? extends Integer> x, Class<Long> y, int z);

    void c(Class<Long> x, Class<? extends Integer> y, float z);

    void d(String s, int i);

    void d(int i);
}
