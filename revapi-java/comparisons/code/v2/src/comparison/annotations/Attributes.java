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
package comparison.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public abstract class Attributes {

    @Target(ElementType.METHOD)
    public @interface Anno {
        int a() default 0;
        int b();
    }

    @Anno(a = 1, b = 2)
    @Deprecated
    public abstract void m(int i);
}
