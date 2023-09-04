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

import static java.util.Arrays.asList;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;

/**
 * While {@link org.revapi.Element elements} of a single API analyzer are by definition mutually comparable, the same
 * doesn't apply for reports. Reports are collected across multiple api analyzers and therefore 2 reports, coming from 2
 * different api analyzers can contain elements that are not mutually comparable.
 *
 * <p>
 * This comparator tries to overcome that and offer a way of ordering all the reports in some predictable order.
 */
public class ReportComparator implements Comparator<Report> {

    private final List<Class<?>> baseTypeOrder;
    private final Map<Class<?>, Comparator<?>> perTypeComparators;
    private final Strategy comparisonStrategy;

    protected ReportComparator(List<Class<?>> baseTypeOrder, Map<Class<?>, Comparator<?>> perTypeComparators,
            Strategy comparisonStrategy) {
        this.baseTypeOrder = baseTypeOrder;
        this.perTypeComparators = perTypeComparators;
        this.comparisonStrategy = comparisonStrategy;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public int compare(Report o1, Report o2) {
        Element el1 = getElement(o1);
        Element el2 = getElement(o2);

        Class<?> e1Type = getBaseType(getElement(o1).getClass());
        Class<?> e2Type = getBaseType(getElement(o2).getClass());

        if (e1Type == e2Type) {
            return compare(el1, el2);
        } else {
            int order1 = baseTypeOrder.indexOf(e1Type);
            int order2 = baseTypeOrder.indexOf(e2Type);

            if (order1 == -1) {
                return defaultCompareIncomparable(el1, el2);
            } else {
                if (order2 == -1) {
                    return defaultCompareIncomparable(el1, el2);
                } else {
                    return order1 - order2;
                }
            }
        }
    }

    /**
     * This method is called to compare the two elements that are of different base types and no explicit order has been
     * set for the base types.
     *
     * The default implementation just uses the class names of the two elements for the comparison.
     */
    protected int defaultCompareIncomparable(Element<?> el1, Element<?> el2) {
        return el1.getClass().getName().compareTo(el2.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    protected <E extends Element<E>> Comparator<E> getComparatorFor(E element) {
        return (Comparator<E>) perTypeComparators.getOrDefault(getBaseType(element.getClass()),
                Comparator.naturalOrder());
    }

    /**
     * Compares the two elements based on the comparison strategy. It uses the comparator returned from
     * {@link #getComparatorFor(Element)} for direct comparisons of any two elements.
     *
     * @param e1
     *            the first element
     * @param e2
     *            the second element
     * @param <E>
     *            the base type of the elements
     *
     * @return the result of the comparison
     */
    protected <E extends Element<E>> int compare(E e1, E e2) {
        Comparator<E> comparator = getComparatorFor(e1);
        switch (comparisonStrategy) {
        case DIRECT:
            return comparator.compare(e1, e2);
        case HIERARCHICAL:
            Deque<E> r1Ancestry = new ArrayDeque<>(4);
            Deque<E> r2Ancestry = new ArrayDeque<>(4);

            while (e1 != null) {
                r1Ancestry.push(e1);
                e1 = e1.getParent();
            }

            while (e2 != null) {
                r2Ancestry.push(e2);
                e2 = e2.getParent();
            }

            while (!r1Ancestry.isEmpty() && !r2Ancestry.isEmpty()) {
                int order = comparator.compare(r1Ancestry.pop(), r2Ancestry.pop());
                if (order != 0) {
                    return order;
                }
            }

            return r1Ancestry.size() - r2Ancestry.size();
        default:
            throw new AssertionError("Unsupported report comparison strategy. This is a severe bug in Revapi.");
        }
    }

    /**
     * The elements are always derived from some base type, all subclasses of which must be mutually comparable. This
     * method finds such base type.
     *
     * @param elementClass
     *            the type of some element
     *
     * @return the base type for comparison
     */
    protected static Class<?> getBaseType(Class<?> elementClass) {
        TypeFactory fac = TypeFactory.defaultInstance();
        JavaType type = fac.constructSimpleType(elementClass, new JavaType[0]);
        JavaType elType = type.findSuperType(Element.class);
        return elType.getBindings().getBoundType(0).getRawClass();
    }

    /**
     * Returns the new element or, if the new element is null, the old element of the report. Thus the result is always
     * a non-null value.
     */
    protected Element<?> getElement(Report report) {
        return report.getNewElement() == null ? report.getOldElement() : report.getNewElement();
    }

    /**
     * The comparison strategy defines the way the elements of the same type are compared with each other.
     */
    public enum Strategy {
        /**
         * The result of a comparison is determines solely by directly comparing the two elements of the same type
         */
        DIRECT,

        /**
         * The result of a comparison of two elements is determined by first recursively determining the order of the
         * parents of the two elements and only if the parents are equal to each other is the order determined by
         * comparing the two elements.
         *
         * Note that this is quite heavy way of comparing the elements.
         */
        HIERARCHICAL
    }

    public static class Builder {
        protected final List<Class<?>> baseTypeOrder = new ArrayList<>();
        protected final Map<Class<?>, Comparator<?>> perTypeComparators = new HashMap<>();
        protected Strategy comparisonStrategy = Strategy.DIRECT;

        /**
         * Sets up the report comparator with a custom comparator for a certain types of report elements. I.e. 2 reports
         * with the elements with the same base type will be compared using the provided comparator instead of their
         * natural order.
         *
         * @param elementBaseType
         *            the base type of an element
         * @param comparator
         *            the comparator to use
         * @param <E>
         *            the base type of elements
         *
         * @return this instance
         */
        public <E extends Element<E>> Builder comparingElements(Class<E> elementBaseType, Comparator<E> comparator) {
            perTypeComparators.put(getBaseType(elementBaseType), comparator);
            return this;
        }

        /**
         * This sets up the comparator to order the reports of 2 different element types according to the position of
         * the element base types in the provided array. If no explicit order can be found for 2 element types, they are
         * by default ordered using their class names.
         *
         * @param elementBaseTypes
         *            the list of element base types
         *
         * @return this instance
         */
        public Builder withExplicitOrder(Class<?>... elementBaseTypes) {
            baseTypeOrder.addAll(asList(elementBaseTypes));

            return this;
        }

        public Builder withComparisonStrategy(Strategy comparisonStrategy) {
            this.comparisonStrategy = comparisonStrategy;
            return this;
        }

        /**
         * Constructs a new Report comparator instance with the configured behavior.
         */
        public ReportComparator build() {
            return new ReportComparator(baseTypeOrder, perTypeComparators, comparisonStrategy);
        }
    }
}
