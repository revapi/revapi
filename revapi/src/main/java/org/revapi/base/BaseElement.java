/*
 * Copyright 2014-2021 Lukas Krejci
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
package org.revapi.base;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.API;
import org.revapi.Archive;
import org.revapi.Element;
import org.revapi.query.Filter;

/**
 * A base class for API elements. It is not mandatory to inherit from this class but it provides a good base
 * implementation for most of the cases.
 *
 * <p>The type parameter, {@code <E>}, denotes the parent type of all elements produced by a certain
 * {@link org.revapi.ApiAnalyzer}. All these types are assumed to be mutually comparable (and therefore implement
 * {@link Comparable Comparable&lt;E&gt;}).
 *
 * @param <E> the parent type of all elements in the API
 */
public abstract class BaseElement<E extends Element<E>> implements Element<E>, Cloneable {
    private final API api;
    private Archive archive;
    private E parent;
    private SortedSet<E> children;

    /**
     *
     * @param api
     */
    protected BaseElement(API api) {
        this(api, null);
    }

    protected BaseElement(API api, @Nullable Archive archive) {
        this.api = api;
        this.archive = archive;
    }

    /**
     * Casts "this" to {@code E}. This is unsafe from the language perspective but we suppose {@code E} is the base
     * type of all elements of given API analyzer, so this should always be safe.
     *
     * @return this instance as the base element type
     */
    @SuppressWarnings("unchecked")
    protected E castThis() {
        return (E) this;
    }

    @Nonnull
    @Override
    public API getApi() {
        return api;
    }

    @Nullable
    @Override
    public Archive getArchive() {
        return archive;
    }

    protected void setArchive(@Nullable Archive archive) {
        this.archive = archive;
    }

    @Nonnull
    @Override
    public String getFullHumanReadableString() {
        return toString();
    }

    @Nullable
    @Override
    public E getParent() {
        return parent;
    }

    /**
     * Sets the parent element. No other processing is automagically done (i.e. the parent's children set is <b>NOT</b>
     * updated by calling this method).
     *
     * @param parent the new parent element
     */
    @Override
    public void setParent(@Nullable E parent) {
        this.parent = parent;
    }

    @Nonnull
    @Override
    public SortedSet<E> getChildren() {
        if (children == null) {
            children = new ParentPreservingSet(newChildrenInstance());
        }
        return children;
    }

    /**
     * Returns a shallow copy of this element. In particular, its parent and children will be cleared.
     * @return a copy of this element
     */
    @Override
    public BaseElement<E> clone() {
        try {
            @SuppressWarnings("unchecked")
            BaseElement<E> ret = (BaseElement<E>) super.clone();
            ret.parent = null;
            ret.children = null;

            return ret;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("All base elements need to be cloneable.", e);
        }
    }

    /**
     * Override this method if you need some specialized instance of sorted set or want to do some custom pre-populating
     * or initialization of the children. This default implementation merely returns an empty new
     * {@link java.util.TreeSet} instance.
     *
     * @return a new sorted set instance to store the children in
     */
    @Nonnull
    protected SortedSet<E> newChildrenInstance() {
        return new TreeSet<>();
    }

    @Nonnull
    @Override
    public <T extends Element<E>> List<T> searchChildren(@Nonnull Class<T> resultType, boolean recurse,
            @Nullable Filter<? super T> filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Element<E>> void searchChildren(@Nonnull List<T> results, @Nonnull Class<T> resultType,
            boolean recurse, @Nullable Filter<? super T> filter) {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public <T extends Element<E>> Iterator<T> iterateOverChildren(@Nonnull Class<T> resultType, boolean recurse,
            @Nullable Filter<? super T> filter) {
        throw new UnsupportedOperationException();
    }

    private class ParentPreservingSet implements SortedSet<E> {
        private final SortedSet<E> set;

        private ParentPreservingSet(SortedSet<E> set) {
            this.set = set;
        }

        @Override
        public boolean add(E element) {
            boolean ret = set.add(element);
            if (ret) {
                element.setParent(castThis());
            }

            return ret;
        }

        @Override
        public boolean addAll(@Nonnull Collection<? extends E> c) {
            boolean updated = false;
            for (E e : c) {
                updated |= add(e);
            }

            return updated;
        }

        @Override
        public void clear() {
            for (E e : this) {
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
        public Iterator<E> iterator() {
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
            //noinspection SuspiciousToArrayCall
            return set.toArray(a);
        }

        @Override
        public boolean remove(Object o) {
            Iterator<E> it = set.iterator();
            while (it.hasNext()) {
                E e = it.next();
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
        public Comparator<? super E> comparator() {
            return set.comparator();
        }

        @Nonnull
        @Override
        public SortedSet<E> subSet(E fromElement, E toElement) {
            return new ParentPreservingSet(set.subSet(fromElement, toElement));
        }

        @Nonnull
        @Override
        public SortedSet<E> headSet(E toElement) {
            return new ParentPreservingSet(set.headSet(toElement));
        }

        @Nonnull
        @Override
        public SortedSet<E> tailSet(E fromElement) {
            return new ParentPreservingSet(set.tailSet(fromElement));
        }

        @Override
        public E first() {
            return set.first();
        }

        @Override
        public E last() {
            return set.last();
        }

        private class ParentPreservingIterator implements Iterator<E> {
            private final Iterator<E> it;
            E last;

            private ParentPreservingIterator(Iterator<E> it) {
                this.it = it;
            }

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public E next() {
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
}
