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
package org.revapi.query;

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.Element;

/**
 * Recursively walks an element forest in a depth-first manner leaving out elements not matching the optionally provided
 * filter.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public class DFSFilteringIterator<E extends Element> implements Iterator<E> {
    private final Class<? extends E> resultClass;
    private final Deque<Iterator<? extends Element>> dfsStack = new LinkedList<>();
    private final Filter<? super E> filter;
    private E current;

    /**
     * Constructor.
     *
     * @param rootIterator the iterator over the root elements of the forest
     * @param resultClass  the class of the elements to look for in the forest. All the returned elements will be
     *                     assignable to this class.
     * @param filter       optional filter that further filters out unwanted elements.
     */
    public DFSFilteringIterator(@Nonnull Iterator<? extends Element> rootIterator,
        @Nonnull Class<? extends E> resultClass,
        @Nullable Filter<? super E> filter) {
        dfsStack.push(rootIterator);
        this.resultClass = resultClass;
        this.filter = filter;
    }

    @Override
    public boolean hasNext() {
        if (current != null) {
            return true;
        } else {
            while (true) {
                while (!dfsStack.isEmpty() && !dfsStack.peek().hasNext()) {
                    dfsStack.pop();
                }

                if (dfsStack.isEmpty()) {
                    return false;
                }

                Iterator<? extends Element> currentIterator = dfsStack.peek();

                while (currentIterator.hasNext()) {
                    Element next = currentIterator.next();
                    while (next == null && currentIterator.hasNext()) {
                        next = currentIterator.next();
                    }

                    if (next == null) {
                        break;
                    }

                    boolean found = false;
                    if (resultClass.isAssignableFrom(next.getClass())) {
                        E cur = resultClass.cast(next);

                        if (filter == null || filter.applies(cur)) {
                            current = cur;
                            found = true;
                        }
                    }

                    // we're doing DFS, so once we report this element, we want to start reporting its children
                    // even if we don't report the current element, we might report one of its children so we base
                    // our decision on whether to descend or not regardless of whether we're reporting the current
                    // element or not
                    boolean descend = filter == null || filter.shouldDescendInto(next);
                    if (descend) {
                        Iterator<? extends Element> childIterator = next.getChildren().iterator();
                        if (childIterator.hasNext()) {
                            dfsStack.push(childIterator);
                        }
                    }

                    if (found) {
                        return true;
                    }

                    if (descend) {
                        break;
                    }
                }
            }
        }
    }

    @Override
    public E next() {
        if (current == null && !hasNext()) {
            throw new NoSuchElementException();
        }

        E ret = current;
        current = null;

        return ret;
    }

    /**
     * @throws UnsupportedOperationException This is not supported.
     */
    @Override
    public void remove() {
        //is this worth implementing?
        throw new UnsupportedOperationException();
    }
}
