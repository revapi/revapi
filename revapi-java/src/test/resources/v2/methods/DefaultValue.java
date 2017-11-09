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
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public @interface DefaultValue {

    public @interface Value {
        int value() default 0;
        float newValueWithDefault() default 1.0f;
        Target def() default @Target(ElementType.ANNOTATION_TYPE);
    }

    Value[] annotationArray() default { @Value, @Value(def = @Target(ElementType.CONSTRUCTOR)), @Value(1) };

    Value annotation();

    String stringAttr() default "kachnicky";

    String[] stringArray() default {"kachny", "kachny", "kachnicky"};

    Class<?> classAttr() default void.class;

    Class<?>[] classesAttr() default {String.class, int.class, void.class};

    float newValueWithoutDefault();
}
