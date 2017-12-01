package org.revapi.java.model;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.function.Consumer;

import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleElementVisitor8;
import javax.lang.model.util.SimpleTypeVisitor8;

import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;

final class UseSiteUpdatingSortedSet<T> implements SortedSet<T> {
    private final ProbingEnvironment environment;
    private final SortedSet<T> set;

    UseSiteUpdatingSortedSet(ProbingEnvironment environment, SortedSet<T> set) {
        this.environment = environment;
        this.set = set;
    }

    @Override
    public boolean remove(Object o) {
        if (set.remove(o)) {
            removeFromUseSites(o);
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
            removeFromUseSites(o);
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
                it.remove();
                removeFromUseSites(current);
            }
        };
    }

    private void removeFromUseSites(Object element) {
        if (element instanceof JavaElement) {
            withUsedTypes((JavaElement) element, usedType -> {
                JavaTypeElement usedTypeModel = environment.getModelElement(usedType);
                if (usedTypeModel == null) {
                    return;
                }

                usedTypeModel.getUseSites().removeIf(site -> site.getSite().equals(element));

                if (!usedTypeModel.isInApiThroughUse() || !usedTypeModel.getUseSites().isEmpty()) {
                    return;
                }

                if (usedTypeModel.getParent() != null) {
                    usedTypeModel.getParent().getChildren().remove(usedTypeModel);
                } else {
                    environment.getTree().getRootsUnsafe().remove(usedTypeModel);
                }
            });
        }
    }

    private void withUsedTypes(JavaElement model, Consumer<javax.lang.model.element.TypeElement> action) {

        if (model instanceof JavaAnnotationElement) {
            ((JavaAnnotationElement) model).getAnnotation().getAnnotationType().asElement()
                    .accept(new SimpleElementVisitor8<Void, Void>() {
                        @Override
                        public Void visitType(javax.lang.model.element.TypeElement e, Void __) {
                            action.accept(e);
                            return null;
                        }
                    }, null);
        } else if (model instanceof JavaModelElement) {
            ((JavaModelElement) model).getModelRepresentation().accept(new SimpleTypeVisitor8<Void, Void>() {
                @Override
                public Void visitArray(ArrayType t, Void aVoid) {
                    t.getComponentType().accept(this, null);
                    visitAnnotations(t);
                    return null;
                }

                @Override
                public Void visitDeclared(DeclaredType t, Void __) {
                    t.asElement().accept(new SimpleElementVisitor8<Void, Void>() {
                        @Override
                        public Void visitType(TypeElement e, Void ___) {
                            action.accept(e);
                            return null;
                        }
                    }, null);
                    t.getTypeArguments().forEach(a -> a.accept(this, null));
                    visitAnnotations(t);
                    return null;
                }

                @Override
                public Void visitTypeVariable(TypeVariable t, Void aVoid) {
                    t.getLowerBound().accept(this, null);
                    t.getUpperBound().accept(this, null);
                    visitAnnotations(t);
                    return null;
                }

                @Override
                public Void visitWildcard(WildcardType t, Void aVoid) {
                    if (t.getExtendsBound() != null) {
                        t.getExtendsBound().accept(this, null);
                    }

                    if (t.getSuperBound() != null) {
                        t.getSuperBound().accept(this, null);
                    }

                    visitAnnotations(t);
                    return null;
                }

                @Override
                public Void visitExecutable(ExecutableType t, Void aVoid) {
                    t.getParameterTypes().forEach(p -> p.accept(this, null));
                    t.getReturnType().accept(this, null);
                    t.getThrownTypes().forEach(p -> p.accept(this, null));
                    t.getTypeVariables().forEach(v -> v.accept(this, null));
                    visitAnnotations(t);
                    return null;
                }

                @Override
                public Void visitIntersection(IntersectionType t, Void aVoid) {
                    t.getBounds().forEach(b -> b.accept(this, null));
                    return null;
                }

                private void visitAnnotations(AnnotatedConstruct construct) {
                    for (AnnotationMirror annotationMirror : construct.getAnnotationMirrors()) {
                        annotationMirror.getAnnotationType().accept(this, null);
                    }
                }
            }, null);
        }
    }
}
