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
package org.revapi.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import org.junit.jupiter.api.Test;
import org.revapi.API;
import org.revapi.Archive;

class JacksonElementTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testToString() {
        TestElement top = new TestElement(null, null, "file", mapper.createArrayNode(), 0);
        TestElement first = new TestElement(null, null, "file", mapper.createObjectNode(), 0);
        TestElement second = new TestElement(null, null, "file", mapper.createObjectNode(), 1);
        TestElement third = new TestElement(null, null, "file", new IntNode(42), "x");

        top.getChildren().add(first);
        top.getChildren().add(second);
        second.getChildren().add(third);

        String firstString = first.getFullHumanReadableString();
        String secondString = second.getFullHumanReadableString();
        String thirdString = third.getFullHumanReadableString();

        assertEquals("file:/0", firstString);
        assertEquals("file:/1", secondString);
        assertEquals("file:/1/x", thirdString);
    }

    private static final class TestElement extends JacksonElement<TestElement> {

        public TestElement(API api, Archive archive, String filePath, TreeNode node, String key) {
            super(api, archive, filePath, node, key);
        }

        public TestElement(API api, Archive archive, String filePath, TreeNode node, int index) {
            super(api, archive, filePath, node, index);
        }
    }
}
