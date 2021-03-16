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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;
import org.revapi.FilterFinishResult;
import org.revapi.FilterStartResult;
import org.revapi.Ternary;
import org.revapi.TreeFilter;

class IncludeExcludeTreeFilterTest {

    @Test
    void testUndecidedByDefault() {
        IncludeExcludeTreeFilter<DummyElement> filter = new IncludeExcludeTreeFilter<>(null, null);

        DummyElement a = new DummyElement(null);
        FilterStartResult start = filter.start(a);
        FilterFinishResult finish = filter.finish(a);

        assertSame(Ternary.UNDECIDED, start.getMatch());
        assertSame(Ternary.UNDECIDED, start.getDescend());
        assertTrue(start.isInherited());
        assertSame(Ternary.UNDECIDED, finish.getMatch());
        assertTrue(finish.isInherited());
    }

    @Test
    void testOnlyIncludeSpecified() {
        IncludeExcludeTreeFilter<DummyElement> filter = new IncludeExcludeTreeFilter<>(match("a"), null);
        DummyElement a = new DummyElement("a");
        DummyElement b = new DummyElement("b");

        FilterStartResult startA = filter.start(a);
        FilterFinishResult finishA = filter.finish(a);

        FilterStartResult startB = filter.start(b);
        FilterFinishResult finishB = filter.finish(b);

        assertSame(Ternary.TRUE, startA.getMatch());
        assertSame(Ternary.UNDECIDED, startA.getDescend());
        assertFalse(startA.isInherited());
        assertSame(Ternary.TRUE, finishA.getMatch());
        assertFalse(finishA.isInherited());

        assertSame(Ternary.FALSE, startB.getMatch());
        assertSame(Ternary.UNDECIDED, startB.getDescend());
        assertFalse(startB.isInherited());
        assertSame(Ternary.FALSE, finishB.getMatch());
        assertFalse(finishB.isInherited());
    }

    @Test
    void testExcludeFromAll() {
        IncludeExcludeTreeFilter<DummyElement> filter = new IncludeExcludeTreeFilter<>(null, match("b"));
        DummyElement a = new DummyElement("a");
        DummyElement b = new DummyElement("b");

        FilterStartResult startA = filter.start(a);
        FilterFinishResult finishA = filter.finish(a);

        FilterStartResult startB = filter.start(b);
        FilterFinishResult finishB = filter.finish(b);

        assertSame(Ternary.TRUE, startA.getMatch());
        assertSame(Ternary.UNDECIDED, startA.getDescend());
        assertTrue(startA.isInherited());
        assertSame(Ternary.TRUE, finishA.getMatch());
        assertTrue(finishA.isInherited());

        assertSame(Ternary.FALSE, startB.getMatch());
        assertSame(Ternary.UNDECIDED, startB.getDescend());
        assertTrue(startB.isInherited());
        assertSame(Ternary.FALSE, finishB.getMatch());
        assertTrue(finishB.isInherited());
    }

    @Test
    void testExcludeFromInclude() {
        IncludeExcludeTreeFilter<DummyElement> filter = new IncludeExcludeTreeFilter<>(match("a"), match("b"));
        DummyElement a = new DummyElement("a");
        DummyElement b = new DummyElement("b");
        a.getChildren().add(b);

        FilterStartResult startA = filter.start(a);

        FilterStartResult startB = filter.start(b);
        FilterFinishResult finishB = filter.finish(b);

        FilterFinishResult finishA = filter.finish(a);

        assertSame(Ternary.TRUE, startA.getMatch());
        assertSame(Ternary.UNDECIDED, startA.getDescend());
        assertFalse(startA.isInherited());
        assertSame(Ternary.TRUE, finishA.getMatch());
        assertFalse(finishA.isInherited());

        assertSame(Ternary.FALSE, startB.getMatch());
        assertSame(Ternary.UNDECIDED, startB.getDescend());
        assertFalse(startB.isInherited());
        assertSame(Ternary.FALSE, finishB.getMatch());
        assertFalse(finishB.isInherited());
    }

    @Test
    void testSetsInherited() {
        IncludeExcludeTreeFilter<DummyElement> filter = new IncludeExcludeTreeFilter<>(match("a"), null);
        DummyElement a = new DummyElement("a");
        DummyElement b = new DummyElement("b");
        a.getChildren().add(b);

        FilterStartResult startA = filter.start(a);

        FilterStartResult startB = filter.start(b);
        FilterFinishResult finishB = filter.finish(b);

        FilterFinishResult finishA = filter.finish(a);

        assertSame(Ternary.TRUE, startA.getMatch());
        assertSame(Ternary.UNDECIDED, startA.getDescend());
        assertFalse(startA.isInherited());
        assertSame(Ternary.TRUE, finishA.getMatch());
        assertFalse(finishA.isInherited());

        assertSame(Ternary.TRUE, startB.getMatch());
        assertSame(Ternary.UNDECIDED, startB.getDescend());
        assertTrue(startB.isInherited());
        assertSame(Ternary.TRUE, finishB.getMatch());
        assertTrue(finishB.isInherited());
    }

    static TreeFilter<DummyElement> match(String nameRegex) {
        Pattern pattern = Pattern.compile(nameRegex);
        return new IndependentTreeFilter<DummyElement>() {
            @Override
            protected FilterStartResult doStart(DummyElement element) {
                Ternary res = Ternary.fromBoolean(pattern.matcher(element.name).matches());
                return FilterStartResult.from(res, Ternary.UNDECIDED, false);
            }
        };
    }

    static final class DummyElement extends BaseElement<DummyElement> {
        private final String name;

        DummyElement(String name) {
            super(null);
            this.name = name;
        }

        @Nonnull
        @Override
        public String getFullHumanReadableString() {
            return name;
        }

        @Override
        public int compareTo(DummyElement o) {
            return name.compareTo(o.name);
        }
    }
}
