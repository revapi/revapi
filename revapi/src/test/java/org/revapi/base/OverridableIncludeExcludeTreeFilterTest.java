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
import static org.revapi.base.IncludeExcludeTreeFilterTest.match;

import org.junit.jupiter.api.Test;
import org.revapi.FilterFinishResult;
import org.revapi.FilterStartResult;
import org.revapi.Ternary;

class OverridableIncludeExcludeTreeFilterTest {

    @Test
    void testIncludeFromExclude() {
        IncludeExcludeTreeFilter<IncludeExcludeTreeFilterTest.DummyElement> filter = new IncludeExcludeTreeFilter<>(
                match("b"), match("a"));
        IncludeExcludeTreeFilterTest.DummyElement a = new IncludeExcludeTreeFilterTest.DummyElement("a");
        IncludeExcludeTreeFilterTest.DummyElement b = new IncludeExcludeTreeFilterTest.DummyElement("b");
        a.getChildren().add(b);

        FilterStartResult startA = filter.start(a);

        FilterStartResult startB = filter.start(b);
        FilterFinishResult finishB = filter.finish(b);

        FilterFinishResult finishA = filter.finish(a);

        assertSame(Ternary.FALSE, startA.getMatch());
        assertSame(Ternary.UNDECIDED, startA.getDescend());
        assertFalse(startA.isInherited());
        assertSame(Ternary.FALSE, finishA.getMatch());
        assertFalse(finishA.isInherited());

        assertSame(Ternary.TRUE, startB.getMatch());
        assertSame(Ternary.UNDECIDED, startB.getDescend());
        assertFalse(startB.isInherited());
        assertSame(Ternary.TRUE, finishB.getMatch());
        assertFalse(finishB.isInherited());
    }
}
