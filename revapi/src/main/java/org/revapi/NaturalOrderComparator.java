/*
 * Copyright 2014-2023 Lukas Krejci
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
package org.revapi;

import java.util.Comparator;

/**
 * A comparator using the natural order of comparable objects.
 *
 * @author Lukas Krejci
 *
 * @since 0.4.0
 */
final class NaturalOrderComparator<E> implements Comparator<E> {
    @Override
    @SuppressWarnings("unchecked")
    public int compare(E o1, E o2) {
        return ((Comparable<E>) o1).compareTo(o2);
    }
}
