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

import static java.util.Collections.emptySet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.revapi.API;
import org.revapi.Reference;

class BaseElementTest {

    @Test
    void testReferences() {
        API api = API.of().build();
        TestElement user = new TestElement(api);
        TestElement used1 = new TestElement(api);
        TestElement used2 = new TestElement(api);
        user.getChildren().add(used2);

        user.getReferencedElements().add(new Reference<>(used1, UseType.USE_TYPE));
        user.getReferencedElements().add(new Reference<>(used2, UseType.USE_TYPE));
        used2.getReferencedElements().add(new Reference<>(user, UseType.USE_TYPE));

        assertEquals(references(used1, used2), user.getReferencedElements());
        assertEquals(emptySet(), used1.getReferencedElements());
        assertEquals(references(user), used2.getReferencedElements());

        assertEquals(references(used2), user.getReferencingElements());
        assertEquals(references(user), used1.getReferencingElements());
        assertEquals(references(user), used2.getReferencingElements());

        assertEquals(references(user, used1, used2), user.getCumulativeReferencedElements());
    }

    private static final class TestElement extends BaseElement<TestElement> {
        TestElement(API api) {
            super(api);
        }

        @Override
        public int compareTo(TestElement o) {
            return 0;
        }
    }

    private enum UseType implements Reference.Type<TestElement> {
        USE_TYPE;

        @Override
        public String getName() {
            return name();
        }
    }

    static Set<Reference<TestElement>> references(TestElement... elements) {
        return Stream.of(elements).map(e -> new Reference<>(e, UseType.USE_TYPE)).collect(Collectors.toSet());
    }

}