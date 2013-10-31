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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public class InsertionOrderSortedSet<E> extends AbstractList<E> implements SortedSet<E> {

    private final List<E> list;

    public InsertionOrderSortedSet() {
        list = new ArrayList<>();
    }

    public InsertionOrderSortedSet(Collection<? extends E> c) {
        list = new ArrayList<>(c);
    }

    public InsertionOrderSortedSet(int initialCapacity) {
        list = new ArrayList<>(initialCapacity);
    }

    protected InsertionOrderSortedSet(List<E> subList, boolean unused) {
        list = subList;
    }

    @Override
    public Comparator<? super E> comparator() {
        return new Comparator<E>() {
            @Override
            public int compare(E o1, E o2) {
                return indexOf(o1) - indexOf(o2);
            }
        };
    }

    @Override
    public boolean add(E e) {
        return list.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return list.remove(o);
    }

    @Override
    public E get(int index) {
        return list.get(index);
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
        int from = indexOf(fromElement);
        int to = indexOf(toElement);

        if (from < 0) {
            throw new IllegalArgumentException("fromElement not present in this set");
        } else if (to < 0) {
            throw new IllegalArgumentException("toElement not present in this set");
        } else if (from > to) {
            throw new IllegalArgumentException("fromElement greater than toElement");
        }

        return new InsertionOrderSortedSet<>(subList(from, to), true);
    }

    @Override
    public SortedSet<E> headSet(E toElement) {
        int to = indexOf(toElement);

        if (to < 0) {
            throw new IllegalArgumentException("toElement not present in this set");
        }

        return new InsertionOrderSortedSet<>(subList(0, to), true);
    }

    @Override
    public SortedSet<E> tailSet(E fromElement) {
        int from = indexOf(fromElement);

        if (from < 0) {
            throw new IllegalArgumentException("fromElement not present in this set");
        }

        return new InsertionOrderSortedSet<>(subList(from, size()), true);
    }

    @Override
    public E first() {
        return get(0);
    }

    @Override
    public E last() {
        return get(size() - 1);
    }
}
