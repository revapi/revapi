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
package org.revapi;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.revapi.EditDistance.compute;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

public class EditDistanceTest {

    @Test
    void testEmpty() {
        List<EditDistance.Pair<Integer>> order = compute(emptyList(), emptyList(), Objects::equals);
        assertTrue(order.isEmpty());
    }

    @Test
    void testNonEmptyEmpty() {
        List<EditDistance.Pair<Integer>> order = compute(singletonList(1), emptyList(), Objects::equals);
        assertEquals(1, order.size());
        assertEquals(1, order.get(0).left);
        assertNull(order.get(0).right);
    }

    @Test
    void testEmptyNonEmpty() {
        List<EditDistance.Pair<Integer>> order = compute(emptyList(), singletonList(1), Objects::equals);
        assertEquals(1, order.size());
        assertEquals(1, order.get(0).right);
        assertNull(order.get(0).left);
    }

    @Test
    void testOrdering() {
        List<EditDistance.Pair<Integer>> order = compute(asList(1, 2, 3, 4), asList(1, 3, 5), Objects::equals);

        assertEquals(4, order.size());

        assertEquals(1, order.get(0).left);
        assertEquals(1, order.get(0).right);

        assertEquals(2, order.get(1).left);
        assertNull(order.get(1).right);

        assertEquals(3, order.get(2).left);
        assertEquals(3, order.get(2).right);

        assertEquals(4, order.get(3).left);
        assertEquals(5, order.get(3).right);
    }
}
