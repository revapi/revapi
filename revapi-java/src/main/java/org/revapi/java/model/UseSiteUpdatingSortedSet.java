package org.revapi.java.model;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.BiConsumer;

import org.revapi.Element;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;

final class UseSiteUpdatingSortedSet<T extends Element> implements SortedSet<T> {
    private final ProbingEnvironment environment;
    private final SortedSet<T> set;

    UseSiteUpdatingSortedSet(ProbingEnvironment environment, SortedSet<T> set) {
        this.environment = environment;
        this.set = set;
    }

    @Override
    public boolean remove(Object o) {
        Element currentParent = o instanceof Element
                ? ((Element) o).getParent()
                : null;

        if (set.remove(o)) {
            removeFromUseSites(o, currentParent);
            return true;
        }

        return false;
    }

    @Override
    public Iterator<T> iterator() {
        return wrap(set.iterator());
    }

    @Override
    public Comparator<? super T> comparator() {
        return set.comparator();
    }

    @Override
    public SortedSet<T> subSet(T fromElement, T toElement) {
        return new UseSiteUpdatingSortedSet<>(environment, set.subSet(fromElement, toElement));
    }

    @Override
    public SortedSet<T> headSet(T toElement) {
        return new UseSiteUpdatingSortedSet<>(environment, set.headSet(toElement));
    }

    @Override
    public SortedSet<T> tailSet(T fromElement) {
        return new UseSiteUpdatingSortedSet<>(environment, set.tailSet(fromElement));
    }

    @Override
    public T first() {
        return set.first();
    }

    @Override
    public T last() {
        return set.last();
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
    public Object[] toArray() {
        return set.toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return set.toArray(a);
    }

    @Override
    public boolean add(T t) {
        return set.add(t);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return set.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        return set.addAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean changed = false;
        Iterator<T> it = iterator();
        while (it.hasNext()) {
            if (!c.contains(it.next())) {
                changed = true;
                it.remove();
            }
        }

        return changed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean ret = false;
        for (Object el : c) {
            ret |= remove(el);
        }
        return ret;
    }

    @Override
    public void clear() {
        for (T o : this) {
            removeFromUseSites(o, o.getParent());
        }
        set.clear();
    }

    private Iterator<T> wrap(Iterator<T> it) {
        return new Iterator<T>() {
            T current;
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public T next() {
                return current = it.next();
            }

            @Override
            public void remove() {
                Element currentParent = current.getParent();
                it.remove();
                removeFromUseSites(current, currentParent);
            }
        };
    }

    private void removeFromUseSites(Object element, Element parent) {
        if (element instanceof JavaElement) {
            BiConsumer<org.revapi.java.model.TypeElement, JavaElement> handleType = (usedType, actualUseSite) -> {
                usedType.getUseSites().removeIf(site -> site.getSite().equals(actualUseSite));

                if (!usedType.isInApiThroughUse() || !usedType.getUseSites().isEmpty()) {
                    return;
                }

                if (usedType.getParent() != null) {
                    usedType.getParent().getChildren().remove(usedType);
                } else {
                    environment.getTree().getRootsUnsafe().remove(usedType);
                }
            };

            JavaElement je = (JavaElement) element;
            withUsedTypes(je, parent, handleType);
            je.stream(JavaElement.class, true).forEach(e -> withUsedTypes(e, je, handleType));
        }
    }

    private void withUsedTypes(JavaElement model, Element parent, BiConsumer<org.revapi.java.model.TypeElement, JavaElement> action) {
        if (model instanceof JavaAnnotationElement) {
            org.revapi.java.model.TypeElement t = (org.revapi.java.model.TypeElement) model.getTypeEnvironment()
                    .getModelElement(((JavaAnnotationElement) model).getAnnotation().getAnnotationType());
            if (t != null) {
                action.accept(t, model);
            }
        } else if (model instanceof JavaModelElement) {
            org.revapi.java.model.TypeElement ownerType = null;
            if (model instanceof org.revapi.java.model.TypeElement) {
                ownerType = (org.revapi.java.model.TypeElement) model;
            } else if (model instanceof FieldElement) {
                ownerType = (org.revapi.java.model.TypeElement) parent;
            } else if (model instanceof MethodElement) {
                ownerType = (org.revapi.java.model.TypeElement) parent;
            } else if (model instanceof MethodParameterElement) {
                ownerType = (org.revapi.java.model.TypeElement) parent.getParent();
            }

            if (ownerType == null) {
                throw new IllegalStateException("Could not determine the owning type of " + model);
            }

            ownerType.getUsedTypes().values().forEach(usersByUsedType -> {
                for (Map.Entry<org.revapi.java.model.TypeElement, Set<JavaModelElement>> e : usersByUsedType.entrySet()) {
                    if (e.getValue().contains(model)) {
                        action.accept(e.getKey(), model);
                    }
                }
            });
        }
    }
}
