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

package org.revapi.simple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.Element;
import org.revapi.query.DFSFilteringIterator;
import org.revapi.query.Filter;
import org.revapi.query.FilteringIterator;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public abstract class SimpleElement implements Element {
    private Element parent;
    private SortedSet<Element> children;

    private static class EmptyIterator<E> implements Iterator<E> {

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public E next() {
            throw new NoSuchElementException();
        }

        @Override
        public void remove() {
            throw new IllegalStateException();
        }
    }

    private class ParentPreservingSet implements SortedSet<Element> {
        private final SortedSet<Element> set;

        private ParentPreservingSet(SortedSet<Element> set) {
            this.set = set;
        }

        @Override
        public boolean add(Element element) {
            boolean ret = set.add(element);
            if (ret) {
                element.setParent(SimpleElement.this);
            }

            return ret;
        }

        @Override
        public boolean addAll(Collection<? extends Element> c) {
            for (Element e : c) {
                add(e);
            }

            return !c.isEmpty();
        }

        @Override
        public void clear() {
            for (Element e : this) {
                e.setParent(null);
            }

            set.clear();
        }

        @Override
        public int size() {
            return set.size();
        }

        @Override
        public boolean isEmpty() {
            return set.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return set.contains(o);
        }

        @Override
        public Iterator<Element> iterator() {
            return new ParentPreservingIterator(set.iterator());
        }

        @Override
        public Object[] toArray() {
            return set.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return set.toArray(a);
        }

        @Override
        public boolean remove(Object o) {
            Iterator<Element> it = this.iterator();
            while (it.hasNext()) {
                Element e = it.next();
                if ((o == null && e == null) || (o != null && o.equals(e))) {
                    it.remove();
                    if (e != null) {
                        e.setParent(null);
                    }
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return set.containsAll(c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean ret = false;
            for (Object o : c) {
                ret |= remove(o);
            }

            return ret;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            boolean ret = false;

            for (Object o : c) {
                if (!contains(o)) {
                    ret = true;
                    remove(o);
                }
            }
            return ret;
        }

        @Override
        public Comparator<? super Element> comparator() {
            return set.comparator();
        }

        @Override
        public SortedSet<Element> subSet(Element fromElement, Element toElement) {
            return set.subSet(fromElement, toElement);
        }

        @Override
        public SortedSet<Element> headSet(Element toElement) {
            return set.headSet(toElement);
        }

        @Override
        public SortedSet<Element> tailSet(Element fromElement) {
            return set.tailSet(fromElement);
        }

        @Override
        public Element first() {
            return set.first();
        }

        @Override
        public Element last() {
            return set.last();
        }

        private class ParentPreservingIterator implements Iterator<Element> {
            private final Iterator<Element> it;
            Element last;

            private ParentPreservingIterator(Iterator<Element> it) {
                this.it = it;
            }

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Element next() {
                last = it.next();
                return last;
            }

            @Override
            public void remove() {
                if (last != null) {
                    last.setParent(null);
                }

                it.remove();
            }
        }
    }

    @Nonnull
    public SortedSet<? extends Element> getChildren() {
        if (children == null) {
            children = new ParentPreservingSet(newChildrenInstance());
        }
        return children;
    }

    @Nonnull
    protected SortedSet<Element> newChildrenInstance() {
        return new TreeSet<>();
    }

    @Override
    @Nullable
    public Element getParent() {
        return parent;
    }

    @Override
    public void setParent(@Nullable Element parent) {
        this.parent = parent;
    }

    @Override
    @Nonnull
    public final <T extends Element> List<T> searchChildren(@Nonnull Class<T> resultType, boolean recurse,
        @Nullable Filter<? super T> filter) {
        List<T> results = new ArrayList<>();
        searchChildren(results, resultType, recurse, filter);
        return results;
    }

    @Override
    @Nonnull
    public final <T extends Element> void searchChildren(@Nonnull List<T> results, @Nonnull Class<T> resultType,
        boolean recurse, @Nullable Filter<? super T> filter) {
        for (Element e : getChildren()) {
            if (resultType.isAssignableFrom(e.getClass())) {
                T te = resultType.cast(e);
                if (filter == null || filter.applies(te)) {
                    results.add(te);
                }
            }

            if (recurse && (filter == null || filter.shouldDescendInto(e))) {
                e.searchChildren(results, resultType, recurse, filter);
            }
        }
    }

    /**
     * Assumes that {@code toString()} can do the job.
     *
     * @see org.revapi.Element#getFullHumanReadableString()
     */
    @Override
    @Nonnull
    public String getFullHumanReadableString() {
        return toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    @Nonnull
    public <T extends Element> Iterator<T> iterateOverChildren(@Nonnull Class<T> resultType, boolean recurse,
        @Nullable Filter<? super T> filter) {

        if (children == null) {
            return new EmptyIterator<>();
        }

        return recurse ? new DFSFilteringIterator<>(getChildren().iterator(), resultType, filter) :
            new FilteringIterator<>((Iterator<T>) getChildren().iterator(), resultType, filter);
    }

    @Nonnull
    protected <T extends Element> List<T> getDirectChildrenOfType(@Nonnull Class<T> type) {
        return searchChildren(type, false, null);
    }
}
