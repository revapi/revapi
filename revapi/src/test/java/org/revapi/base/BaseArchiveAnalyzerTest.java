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

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.revapi.FilterStartResult.doesntMatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;
import org.revapi.API;
import org.revapi.ApiAnalyzer;
import org.revapi.FilterStartResult;
import org.revapi.TreeFilter;

class BaseArchiveAnalyzerTest {
    TestArchiveAnalyzer analyzer = new TestArchiveAnalyzer(null, null)
            .withRoots("a", "b", "c")
            .withChildren("a", "aa", "ab", "ac")
            .withChildren("b", "ba", "bb", "bc")
            .withChildren("c", "ca", "cb", "cc");


    @Test
    void appliesFilteringOnRoots() {
        TreeFilter<TestElement> filter = new IndependentTreeFilter<TestElement>() {
            @Override
            protected FilterStartResult doStart(TestElement element) {
                return element.getFullHumanReadableString().equals("c")
                        ? doesntMatch()
                        : FilterStartResult.matchAndDescend();
            }
        };

        TestForest f = analyzer.analyze(filter);
        assertEquals(2, f.getRoots().size());
        assertTrue(f.getRoots().contains(new TestElement(null, "a")));
        assertTrue(f.getRoots().contains(new TestElement(null, "b")));
        assertFalse(f.getRoots().contains(new TestElement(null, "c")));
    }

    @Test
    void appliesFilteringOnNonRoots() {
        TreeFilter<TestElement> filter = new IndependentTreeFilter<TestElement>() {
            @Override
            protected FilterStartResult doStart(TestElement element) {
                return element.getFullHumanReadableString().equals("cc")
                        ? doesntMatch()
                        : FilterStartResult.matchAndDescend();
            }
        };

        TestForest f = analyzer.analyze(filter);
        assertEquals(3, f.getRoots().size());
        assertTrue(f.getRoots().contains(new TestElement(null, "a")));
        assertTrue(f.getRoots().contains(new TestElement(null, "b")));
        assertTrue(f.getRoots().contains(new TestElement(null, "c")));

        TestElement a = f.getRoots().stream().filter(e -> e.getFullHumanReadableString().equals("a")).findFirst().get();
        TestElement b = f.getRoots().stream().filter(e -> e.getFullHumanReadableString().equals("b")).findFirst().get();
        TestElement c = f.getRoots().stream().filter(e -> e.getFullHumanReadableString().equals("c")).findFirst().get();

        assertEquals(3, a.getChildren().size());
        assertEquals(3, b.getChildren().size());
        assertEquals(2, c.getChildren().size());
        assertTrue(c.getChildren().contains(new TestElement(null, "ca")));
        assertTrue(c.getChildren().contains(new TestElement(null, "cb")));
        assertFalse(c.getChildren().contains(new TestElement(null, "cc")));
    }

    public static final class TestElement extends BaseElement<TestElement> {
        private final String name;

        public TestElement(API api, String name) {
            super(api);
            this.name = name;
        }

        @Override
        public int compareTo(TestElement o) {
            return name.compareTo(o.name);
        }

        @Nonnull
        @Override
        public String getFullHumanReadableString() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TestElement that = (TestElement) o;
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }

    public static final class TestForest extends BaseElementForest<TestElement> {
        public TestForest(API api) {
            super(api);
        }
    }

    public static final class TestArchiveAnalyzer extends BaseArchiveAnalyzer<TestForest, TestElement> {

        private List<TestElement> roots = new ArrayList<>();
        private Map<String, List<TestElement>> children = new HashMap<>();

        public TestArchiveAnalyzer(ApiAnalyzer<TestElement> apiAnalyzer, API api) {
            super(apiAnalyzer, api);
        }

        TestArchiveAnalyzer withRoots(String... names) {
            this.roots.addAll(Stream.of(names).map(n -> new TestElement(getApi(), n)).collect(toList()));
            return this;
        }

        TestArchiveAnalyzer withChildren(String parent, String... children) {
            this.children.put(parent, Stream.of(children).map(n -> new TestElement(getApi(), n)).collect(toList()));
            return this;
        }

        @Override
        protected TestForest newElementForest() {
            return new TestForest(getApi());
        }

        @Override
        protected Stream<TestElement> discoverRoots(Object ctx) {
            return roots.stream();
        }

        @Override
        protected Stream<TestElement> discoverElements(Object ctx, TestElement parent) {
            return children.getOrDefault(parent.name, emptyList()).stream();
        }
    }
}
