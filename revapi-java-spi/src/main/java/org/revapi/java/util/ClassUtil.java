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

package org.revapi.java.util;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class ClassUtil {

    private ClassUtil() {

    }

    @SuppressWarnings("unchecked")
    public static <T> T cast(Object value) {
        return (T) value;
    }

    /**
     * A utility method to transform the provided raw class object into its generified form.
     * <p/>
     * This is useful in situations where you need to pass a generic class as a parameter into some method. There is
     * no way of obtaining a concrete generic class with some instantiation of its type parameters so one needs to use
     * a helper method like this to obtain one.
     * <p/>
     * The intended use is this:
     * <pre><code>
     * ...
     * <p/>
     * &lt;T&gt; T method(Class&lt;T&gt; cls) { ... }
     * <p/>
     * ...
     * <p/>
     * List&lt;String&gt; l = method(ClassUtil.&lt;List&lt;String&gt;&gt;generify(List.class);
     * </code></pre>
     *
     * @param rawClass the class to generify
     * @param <T>      the intended generic instantiation of the supplied class
     *
     * @return the provided class "converted" into its generic instantiation
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> generify(Class<?> rawClass) {
        return (Class<T>) rawClass;
    }
}

