/*
 * Copyright 2013 Lukas Krejci
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

package org.revapi.java.classes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@TestAnnotation(
    param6 = {@Target(ElementType.ANNOTATION_TYPE), @Target(ElementType.CONSTRUCTOR), @Target(ElementType.FIELD)})
public @interface TestAnnotation {
    int param1() default 0;

    Class<?> param2() default String.class;

    Target param3() default @Target(ElementType.ANNOTATION_TYPE);

    int[] param4() default {1, 2};

    String[] param5() default {"1", "2"};

    Target[] param6();
}
