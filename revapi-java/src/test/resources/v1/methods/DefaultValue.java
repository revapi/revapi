/*
 * Copyright $year Lukas Krejci
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public @interface DefaultValue {

    public @interface Value {
        int value();
        Target def() default @Target(ElementType.FIELD);
    }

    Value[] annotationArray() default { @Value(0), @Value(0) };

    Value annotation() default @Value(0);

    String stringAttr() default "kachny";

    String[] stringArray() default {"kachny", "kachny"};

    Class<?> classAttr() default String.class;

    Class<?>[] classesAttr() default {String.class, int.class};
}
