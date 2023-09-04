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
 * A simple implementation of the {@link org.revapi.Element} interface intended to be extended.
 *
 * @author Lukas Krejci
 *
 * @since 0.1
 *
 * @deprecated use {@link org.revapi.base.BaseElement} instead
 */
@Deprecated
public abstract class SimpleElement implements Element, Cloneable {
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
        public boolean addAll(@Nonnull Collection<? extends Element> c) {
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

        @Nonnull
        @Override
        public Iterator<Element> iterator() {
            return new ParentPreservingIterator(set.iterator());
        }

        @Nonnull
        @Override
        public Object[] toArray() {
            return set.toArray();
        }

        @Nonnull
        @Override
        public <T> T[] toArray(@Nonnull T[] a) {
            // noinspection SuspiciousToArrayCall
            return set.toArray(a);
        }

        @Override
        public boolean remove(Object o) {
            Iterator<Element> it = this.iterator();
            while (it.hasNext()) {
                Element e = it.next();
                if (o == null && e == null || o != null && o.equals(e)) {
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
        public boolean containsAll(@Nonnull Collection<?> c) {
            return set.containsAll(c);
        }

        @Override
        public boolean removeAll(@Nonnull Collection<?> c) {
            boolean ret = false;
            for (Object o : c) {
                ret |= remove(o);
            }

            return ret;
        }

        @Override
        public boolean retainAll(@Nonnull Collection<?> c) {
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

        @Nonnull
        @Override
        public SortedSet<Element> subSet(Element fromElement, Element toElement) {
            return set.subSet(fromElement, toElement);
        }

        @Nonnull
        @Override
        public SortedSet<Element> headSet(Element toElement) {
            return set.headSet(toElement);
        }

        @Nonnull
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
                it.remove();
                if (last != null) {
                    last.setParent(null);
                }
            }
        }
    }

    /**
     * Returns a shallow copy of this element. In particular, its parent and children will be cleared.
     *
     * @return a copy of this element
     */
    @Override
    public SimpleElement clone() {
        try {
            SimpleElement ret = (SimpleElement) super.clone();
            ret.parent = null;
            ret.children = null;

            return ret;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("All simple elements need to be cloneable.", e);
        }
    }

    /**
     * This default implementation uses the {@link #newChildrenInstance()} to initialize the children set and wraps it
     * in a private set implementation that automagically changes the parent of the elements based on the membership.
     *
     * @return children of this element
     */
    @Nonnull
    public SortedSet<? extends Element> getChildren() {
        if (children == null) {
            children = new ParentPreservingSet(newChildrenInstance());
        }
        return children;
    }

    /**
     * Override this method if you need some specialized instance of sorted set or want to do some custom pre-populating
     * or initialization of the children. This default implementation merely returns an empty new
     * {@link java.util.TreeSet} instance.
     *
     * @return a new sorted set instance to store the children in
     */
    @Nonnull
    protected SortedSet<Element> newChildrenInstance() {
        return new TreeSet<>();
    }

    /**
     * @return The parent element of this element.
     */
    @Override
    @Nullable
    public Element getParent() {
        return parent;
    }

    /**
     * Sets the parent element. No other processing is automagically done (i.e. the parent's children set is <b>NOT</b>
     * updated by calling this method).
     *
     * @param parent
     *            the new parent element
     */
    @Override
    public void setParent(@Nullable Element parent) {
        this.parent = parent;
    }

    @Override
    @Nonnull
    public final List searchChildren(@Nonnull Class resultType, boolean recurse, @Nullable Filter filter) {
        List results = new ArrayList<>();
        searchChildren(results, resultType, recurse, filter);
        return results;
    }

    @Override
    public final void searchChildren(@Nonnull List results, @Nonnull Class resultType, boolean recurse,
            @Nullable Filter filter) {
        for (Element e : getChildren()) {
            if (resultType.isAssignableFrom(e.getClass())) {
                Object te = resultType.cast(e);
                if (filter == null || filter.applies(te)) {
                    results.add(te);
                }
            }

            if (recurse && (filter == null || filter.shouldDescendInto(e))) {
                e.searchChildren(results, resultType, true, filter);
            }
        }
    }

    /**
     * This default implementation assumes that {@code toString()} can do the job.
     *
     * @return the human readable representation of this element
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
    public Iterator iterateOverChildren(@Nonnull Class resultType, boolean recurse, @Nullable Filter filter) {

        if (children == null) {
            return new EmptyIterator<>();
        }

        return recurse ? new DFSFilteringIterator<>(getChildren().iterator(), resultType, filter)
                : new FilteringIterator<>(getChildren().iterator(), resultType, filter);
    }

    @Nonnull
    protected <T extends Element> List<T> getDirectChildrenOfType(@Nonnull Class<T> type) {
        return searchChildren(type, false, null);
    }
}
