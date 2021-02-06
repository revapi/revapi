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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;
import org.revapi.API;
import org.revapi.ApiAnalyzer;
import org.revapi.Archive;
import org.revapi.ElementForest;
import org.revapi.FilterStartResult;
import org.revapi.Ternary;
import org.revapi.TreeFilter;

class BaseEagerLoadingArchiveAnalyzerTest {

    TestAnalyzer analyzer;
    {
        PrecreatedArchive archive = new PrecreatedArchive("archive",
                setOf(
                        new TestElement("a", 0, setOf(
                                new TestElement("aa", 0),
                                new TestElement("ab", 1))),
                        new TestElement("b", 1, setOf(
                                new TestElement("ba", 0, setOf(
                                        new TestElement("baa", 0)
                                )),
                                new TestElement("bb", 1),
                                new TestElement("bc", 2)
                        ))));
        API api = API.of(archive).build();

        analyzer = new TestAnalyzer(null, api);
    }
    @Test
    void createsHierarchy() {
        ElementForest<TestElement> forest = analyzer.analyze(TreeFilter.matchAndDescend());

        assertEquals(2, forest.getRoots().size());

        TestElement a = forest.getRoots().stream().filter(e -> e.getFullHumanReadableString().equals("a")).findFirst().orElse(null);
        TestElement b = forest.getRoots().stream().filter(e -> e.getFullHumanReadableString().equals("b")).findFirst().orElse(null);

        assertNotNull(a);
        assertNotNull(b);
        assertEquals(2, a.getChildren().size());
        assertEquals(3, b.getChildren().size());

        TestElement aa = a.getChildren().stream().filter(e -> e.getFullHumanReadableString().equals("aa")).findFirst().orElse(null);
        TestElement ab = a.getChildren().stream().filter(e -> e.getFullHumanReadableString().equals("ab")).findFirst().orElse(null);

        assertNotNull(aa);
        assertNotNull(ab);
        assertEquals(0, aa.getChildren().size());
        assertEquals(0, ab.getChildren().size());

        TestElement ba = b.getChildren().stream().filter(e -> e.getFullHumanReadableString().equals("ba")).findFirst().orElse(null);
        TestElement bb = b.getChildren().stream().filter(e -> e.getFullHumanReadableString().equals("bb")).findFirst().orElse(null);
        TestElement bc = b.getChildren().stream().filter(e -> e.getFullHumanReadableString().equals("bc")).findFirst().orElse(null);

        assertNotNull(ba);
        assertNotNull(bb);
        assertNotNull(bc);
        assertEquals(1, ba.getChildren().size());
        assertEquals(0, bb.getChildren().size());
        assertEquals(0, bc.getChildren().size());

        assertEquals("baa", ba.getChildren().iterator().next().getFullHumanReadableString());
    }

    @Test
    void appliesFilter() {
        ElementForest<TestElement> forest = analyzer.analyze(new IndependentTreeFilter<TestElement>() {
            @Override
            protected FilterStartResult doStart(TestElement element) {
                return FilterStartResult.direct(Ternary.fromBoolean(!"ab".equals(element.name) && !"ba".equals(element.name)), Ternary.TRUE);
            }
        });

        assertEquals(2, forest.getRoots().size());

        TestElement a = forest.getRoots().stream().filter(e -> e.getFullHumanReadableString().equals("a")).findFirst().orElse(null);
        TestElement b = forest.getRoots().stream().filter(e -> e.getFullHumanReadableString().equals("b")).findFirst().orElse(null);

        assertNotNull(a);
        assertNotNull(b);
        assertEquals(1, a.getChildren().size());
        assertEquals(2, b.getChildren().size());

        TestElement aa = a.getChildren().stream().filter(e -> e.getFullHumanReadableString().equals("aa")).findFirst().orElse(null);

        assertNotNull(aa);
        assertEquals(0, aa.getChildren().size());

        TestElement bb = b.getChildren().stream().filter(e -> e.getFullHumanReadableString().equals("bb")).findFirst().orElse(null);
        TestElement bc = b.getChildren().stream().filter(e -> e.getFullHumanReadableString().equals("bc")).findFirst().orElse(null);

        assertNotNull(bb);
        assertNotNull(bc);
        assertEquals(0, bb.getChildren().size());
        assertEquals(0, bc.getChildren().size());
    }

    @SafeVarargs
    private static <E> Set<E> setOf(E... elements) {
        return new HashSet<>(Arrays.asList(elements));
    }

    public static final class TestElement extends BaseElement<TestElement> {
        private final String name;
        private final int siblingOrder;

        public TestElement(String name, int siblingOrder) {
            this(name, siblingOrder, Collections.emptySet());
        }

        public TestElement(String name, int siblingOrder, Set<TestElement> children) {
            super(null);
            this.name = name;
            this.siblingOrder = siblingOrder;
            getChildren().addAll(children);
        }

        @Override
        public int compareTo(TestElement o) {
            return siblingOrder - o.siblingOrder;
        }

        @Nonnull
        @Override
        public String getFullHumanReadableString() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final class PrecreatedArchive implements Archive {
        private final String name;
        private final Set<TestElement> roots;

        public PrecreatedArchive(String name,
                Set<TestElement> roots) {
            this.name = name;
            this.roots = roots;
        }

        public Set<TestElement> getRoots() {
            return roots;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public InputStream openStream() {
            return null;
        }
    }

    public static final class TestAnalyzer extends BaseEagerLoadingArchiveAnalyzer<BaseElementForest<TestElement>, TestElement> {

        public TestAnalyzer(
                ApiAnalyzer<TestElement> apiAnalyzer, API api) {
            super(apiAnalyzer, api, false);
        }

        @Override
        protected Set<TestElement> createElements(Archive archive) {
            if (archive instanceof PrecreatedArchive) {
                return ((PrecreatedArchive) archive).getRoots();
            }
            return Collections.emptySet();
        }

        @Override
        protected BaseElementForest<TestElement> newElementForest() {
            return new BaseElementForest<>(getApi());
        }
    }
}
