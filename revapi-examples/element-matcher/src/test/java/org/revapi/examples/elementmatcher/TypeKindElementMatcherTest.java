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
package org.revapi.examples.elementmatcher;

import static java.util.stream.Collectors.toMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.revapi.ApiAnalyzer;
import org.revapi.ArchiveAnalyzer;
import org.revapi.FilterStartResult;
import org.revapi.Ternary;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaTypeElement;

class TypeKindElementMatcherTest {

    private TypeKindElementMatcher matcher = new TypeKindElementMatcher();

    private ArchiveAnalyzer<JavaElement> archiveAnalyzer;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() {
        archiveAnalyzer = mock(ArchiveAnalyzer.class);
        ApiAnalyzer<JavaElement> apiAnalyzer = mock(ApiAnalyzer.class);

        when(archiveAnalyzer.getApiAnalyzer()).thenReturn(apiAnalyzer);
        when(apiAnalyzer.getExtensionId()).thenReturn("revapi.java");
    }

    @ParameterizedTest
    @ValueSource(strings = {"class", "interface", "@interface", "enum"})
    void shouldSupportAllTypeKinds(String typeKind) {
        assertTrue(matcher.compile(typeKind).isPresent());
    }

    @Test
    void shouldFailWithUnknownTypeKind() {
        assertFalse(matcher.compile("that-other-type-kind").isPresent());
    }

    @ParameterizedTest
    @MethodSource("kindsWithMatchingElements")
    void shouldMatchTypeOfKind(String typeKind, JavaTypeElement type) {
        FilterStartResult res = matcher.compile(typeKind).map(r -> r.filterFor(archiveAnalyzer).start(type)).get();
        assertSame(Ternary.TRUE, res.getMatch());
        assertFalse(res.isInherited());
        assertFalse(res.getDescend().toBoolean(true));
    }

    @Test
    void shouldOnlyMatchTypeWithCorrectKind() {
        Map<String, JavaTypeElement> matchingElements = kindsWithMatchingElements()
                .collect(toMap(as -> (String) as.get()[0], as -> (JavaTypeElement) as.get()[1]));

        for (Map.Entry<String, JavaTypeElement> ek : matchingElements.entrySet()) {
            for (Map.Entry<String, JavaTypeElement> ev : matchingElements.entrySet()) {
                FilterStartResult res = matcher.compile(ek.getKey())
                        .map(r -> r.filterFor(archiveAnalyzer).start(ev.getValue())).get();

                Ternary expectedMatch = Ternary.fromBoolean(ek.getKey().equals(ev.getKey()));

                assertSame(expectedMatch, res.getMatch());
                assertFalse(res.isInherited());
                assertFalse(res.getDescend().toBoolean(true));
            }
        }
    }

    static Stream<Arguments> kindsWithMatchingElements() {
        Function<ElementKind, JavaTypeElement> typeOfKind = kind -> {
            JavaTypeElement type = mock(JavaTypeElement.class);
            TypeElement el = mock(TypeElement.class);

            when(type.getDeclaringElement()).thenReturn(el);
            when(el.getKind()).thenReturn(kind);

            return type;
        };

        return Stream.of(
                arguments("class", typeOfKind.apply(ElementKind.CLASS)),
                arguments("interface", typeOfKind.apply(ElementKind.INTERFACE)),
                arguments("@interface", typeOfKind.apply(ElementKind.ANNOTATION_TYPE)),
                arguments("enum", typeOfKind.apply(ElementKind.ENUM)));
    }
}
