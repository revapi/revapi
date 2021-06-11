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
package org.revapi.java.test;

import static org.junit.Assert.assertEquals;

import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;

import org.junit.Rule;
import org.junit.Test;
import org.revapi.java.spi.Util;
import org.revapi.testjars.CompiledJar;
import org.revapi.testjars.junit4.Jar;

public class StringRepresentationTest {

    @Rule
    public Jar jar = new Jar();

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void testSimpleMethodParameterStringRepresentation() throws Exception {
        CompiledJar.Environment env = jar.from().classPathSources(null, "ToStrings.java").build().analyze();

        Element cls = env.elements().getTypeElement("ToStrings");
        List<ExecutableElement> methods = ElementFilter.methodsIn(cls.getEnclosedElements());
        ExecutableElement method = methods.stream()
                .filter(m -> m.getSimpleName().contentEquals("methodWithTypeParamsInMethodParams")).findAny().get();
        VariableElement secondParam = method.getParameters().get(1);

        String humanReadable = Util.toHumanReadableString(secondParam);
        assertEquals(
                "void ToStrings::methodWithTypeParamsInMethodParams(int, ===java.util.function.Function<java.lang.String, ?>===, java.util.HashMap<?, ?>)",
                humanReadable);
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void testGenericMethodParameters() throws Exception {
        CompiledJar.Environment env = jar.from().classPathSources(null, "ToStrings.java").build().analyze();

        TypeElement Generic = env.elements().getTypeElement("ToStrings.Generic");
        List<ExecutableElement> methodsInGeneric = ElementFilter.methodsIn(Generic.getEnclosedElements());
        ExecutableElement m1 = methodsInGeneric.stream().filter(m -> m.getSimpleName().contentEquals("m1")).findFirst()
                .get();

        String expected;

        expected = "<X extends java.lang.Enum<U> & java.lang.Cloneable, Y extends java.util.Set<X>> X ToStrings.Generic<T extends U, U extends java.lang.Enum<U>, E extends java.lang.Throwable>::m1(===U===, java.util.Map<java.util.Comparator<? super T>, java.lang.String>, X) throws E";
        assertEquals(expected, Util.toHumanReadableString(m1.getParameters().get(0)));

        expected = "<X extends java.lang.Enum<U> & java.lang.Cloneable, Y extends java.util.Set<X>> X ToStrings.Generic<T extends U, U extends java.lang.Enum<U>, E extends java.lang.Throwable>::m1(U, ===java.util.Map<java.util.Comparator<? super T>, java.lang.String>===, X) throws E";
        assertEquals(expected, Util.toHumanReadableString(m1.getParameters().get(1)));

        expected = "<X extends java.lang.Enum<U> & java.lang.Cloneable, Y extends java.util.Set<X>> X ToStrings.Generic<T extends U, U extends java.lang.Enum<U>, E extends java.lang.Throwable>::m1(U, java.util.Map<java.util.Comparator<? super T>, java.lang.String>, ===X===) throws E";
        assertEquals(expected, Util.toHumanReadableString(m1.getParameters().get(2)));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void testSelfReferencingTypeVariables() throws Exception {
        CompiledJar.Environment env = jar.from().classPathSources(null, "ToStrings.java").build().analyze();

        TypeElement Enums = env.elements().getTypeElement("ToStrings.Enums");
        Element Enum = ((DeclaredType) Enums.getSuperclass()).asElement();
        List<ExecutableElement> methods = ElementFilter.methodsIn(Enum.getEnclosedElements());
        Element ordinal = methods.stream().filter(m -> m.getSimpleName().contentEquals("ordinal")).findFirst().get();
        Element compareTo = methods.stream().filter(m -> m.getSimpleName().contentEquals("compareTo")).findFirst()
                .get();
        Element valueOf = methods.stream().filter(m -> m.getSimpleName().contentEquals("valueOf")).findFirst().get();

        String repr = Util.toHumanReadableString(Enums);
        assertEquals("ToStrings.Enums", repr);

        repr = Util.toHumanReadableString(Enum);
        assertEquals("java.lang.Enum<E extends java.lang.Enum<E>>", repr);

        repr = Util.toHumanReadableString(Enum.asType());
        assertEquals("java.lang.Enum<E extends java.lang.Enum<E>>", repr);

        repr = Util.toHumanReadableString(ordinal);
        assertEquals("int java.lang.Enum<E extends java.lang.Enum<E>>::ordinal()", repr);

        repr = Util.toHumanReadableString(compareTo);
        assertEquals("int java.lang.Enum<E extends java.lang.Enum<E>>::compareTo(E)", repr);

        repr = Util.toHumanReadableString(valueOf);
        assertEquals(
                "<T extends java.lang.Enum<T>> T java.lang.Enum<E extends java.lang.Enum<E>>::valueOf(java.lang.Class<T>, java.lang.String)",
                repr);
    }

    @Test
    public void testGenericTypeSignatures() throws Exception {
        CompiledJar.Environment env = jar.from().classPathSources(null, "ToStrings.java").build().analyze();

        TypeElement Generic = env.elements().getTypeElement("ToStrings.Generic");
        TypeElement Inner = env.elements().getTypeElement("ToStrings.Generic.Inner");

        String expected;

        expected = "ToStrings.Generic<T extends U, U extends java.lang.Enum<U>, E extends java.lang.Throwable>";

        assertEquals(expected, Util.toHumanReadableString(Generic));
        assertEquals(expected, Util.toHumanReadableString(Generic.asType()));

        expected = "ToStrings.Generic<T extends U, U extends java.lang.Enum<U>, E extends java.lang.Throwable>.Inner<I extends U>";
        assertEquals(expected, Util.toHumanReadableString(Inner));
        assertEquals(expected, Util.toHumanReadableString(Inner.asType()));
    }

    @Test
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void testGenericMethodSignatures() throws Exception {
        CompiledJar.Environment env = jar.from().classPathSources(null, "ToStrings.java").build().analyze();

        TypeElement Generic = env.elements().getTypeElement("ToStrings.Generic");
        TypeElement Inner = env.elements().getTypeElement("ToStrings.Generic.Inner");
        List<ExecutableElement> methodsInGeneric = ElementFilter.methodsIn(Generic.getEnclosedElements());
        List<ExecutableElement> methodsInInner = ElementFilter.methodsIn(Inner.getEnclosedElements());
        ExecutableElement gm1 = methodsInGeneric.stream().filter(m -> m.getSimpleName().contentEquals("m1")).findFirst()
                .get();
        ExecutableElement gm2 = methodsInGeneric.stream().filter(m -> m.getSimpleName().contentEquals("m2")).findFirst()
                .get();
        ExecutableElement im1 = methodsInInner.stream().filter(m -> m.getSimpleName().contentEquals("m1")).findFirst()
                .get();

        String expected;

        expected = "<X extends java.lang.Enum<U> & java.lang.Cloneable, Y extends java.util.Set<X>> X ToStrings.Generic<T extends U, U extends java.lang.Enum<U>, E extends java.lang.Throwable>::m1(U, java.util.Map<java.util.Comparator<? super T>, java.lang.String>, X) throws E";
        assertEquals(expected, Util.toHumanReadableString(gm1));

        // there is no way of getting the executable element from an executable type or any other way of getting at
        // the declaring type in a 100% reliable manner. Therefore we render an executable type without a name
        // and with all type variables from the enclosing type fully specified in place.
        expected = "<X extends java.lang.Enum<U> & java.lang.Cloneable, Y extends java.util.Set<X>> X (U extends java.lang.Enum<U>, java.util.Map<java.util.Comparator<? super T extends U extends java.lang.Enum<U>>, java.lang.String>, X) throws E extends java.lang.Throwable";
        assertEquals(expected, Util.toHumanReadableString(gm1.asType()));

        expected = "E ToStrings.Generic<T extends U, U extends java.lang.Enum<U>, E extends java.lang.Throwable>::m2(U)";
        assertEquals(expected, Util.toHumanReadableString(gm2));

        expected = "E extends java.lang.Throwable (U extends java.lang.Enum<U>)";
        assertEquals(expected, Util.toHumanReadableString(gm2.asType()));

        expected = "T ToStrings.Generic<T extends U, U extends java.lang.Enum<U>, E extends java.lang.Throwable>.Inner<I extends U>::m1(I) throws E";
        assertEquals(expected, Util.toHumanReadableString(im1));

        expected = "T extends U extends java.lang.Enum<U> (I extends U extends java.lang.Enum<U>) throws E extends java.lang.Throwable";
        assertEquals(expected, Util.toHumanReadableString(im1.asType()));
    }

    @Test
    public void testMethodWithGenericParameterInBound() throws Exception {
        CompiledJar.Environment env = jar.from().classPathSources(null, "ToStrings.java").build().analyze();

        TypeElement ToStrings = env.elements().getTypeElement("ToStrings");
        List<ExecutableElement> methods = ElementFilter.methodsIn(ToStrings.getEnclosedElements());
        ExecutableElement method = methods.stream()
                .filter(m -> m.getSimpleName().contentEquals("methodWithGenericParameterInBound" + "")).findFirst()
                .get();

        String expected;

        expected = "<T extends java.lang.Cloneable & java.lang.Comparable<? super T>> java.util.Set<T> ToStrings::methodWithGenericParameterInBound(T)";
        assertEquals(expected, Util.toHumanReadableString(method));
    }

    @Test
    public void issue238_StackOverflowInStringRepresentation() throws Exception {
        CompiledJar.Environment env = jar.from().classPathSources(null, "Issue238.java").build().analyze();

        TypeElement MoneyRange = env.elements().getTypeElement("Issue238.MoneyRange");
        ExecutableElement containsMethod = ElementFilter.methodsIn(MoneyRange.getEnclosedElements()).stream()
                .filter(m -> m.getSimpleName().contentEquals("contains")).findFirst().get();

        String expected = "boolean Issue238.MoneyRange::contains(Issue238.Money)";

        assertEquals(expected, Util.toHumanReadableString(containsMethod));
    }
}
