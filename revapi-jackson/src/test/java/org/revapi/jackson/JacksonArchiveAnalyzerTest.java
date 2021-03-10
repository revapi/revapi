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
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.revapi.API;
import org.revapi.Archive;
import org.revapi.TreeFilter;
import org.revapi.base.BaseElementForest;
import org.revapi.base.InputStreamArchive;

class JacksonArchiveAnalyzerTest {

    @Test
    void testReadsDataFromFile() {
        API api = API.of(
                new InputStreamArchive("emptyObject",
                        () -> new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8))),
                new InputStreamArchive("emptyArray",
                        () -> new ByteArrayInputStream("[]".getBytes(StandardCharsets.UTF_8))),
                new InputStreamArchive("number", () -> new ByteArrayInputStream("42".getBytes(StandardCharsets.UTF_8))),
                new InputStreamArchive("string",
                        () -> new ByteArrayInputStream("\"42\"".getBytes(StandardCharsets.UTF_8))),
                new InputStreamArchive("bool", () -> new ByteArrayInputStream("true".getBytes(StandardCharsets.UTF_8))),
                new InputStreamArchive("null", () -> new ByteArrayInputStream("null".getBytes(StandardCharsets.UTF_8))),
                new InputStreamArchive("object",
                        () -> new ByteArrayInputStream("{\"a\": \"b\"}".getBytes(StandardCharsets.UTF_8))),
                new InputStreamArchive("array",
                        () -> new ByteArrayInputStream("[1, 2]".getBytes(StandardCharsets.UTF_8))))
                .build();

        @SuppressWarnings("unchecked")
        TestAnalyzer analyzer = new TestAnalyzer(mock(JacksonApiAnalyzer.class), api, null, new ObjectMapper(),
                StandardCharsets.UTF_8);

        BaseElementForest<TestElement> forest = analyzer.analyze(TreeFilter.matchAndDescend());

        assertEquals(8, forest.getRoots().size());
        Iterator<TestElement> it = forest.getRoots().iterator();

        // the root nodes are ordered by the name of the archive
        assertEquals(JsonNodeFactory.instance.arrayNode().add(1).add(2), it.next().getNode());
        assertEquals(JsonNodeFactory.instance.booleanNode(true), it.next().getNode());
        assertEquals(JsonNodeFactory.instance.arrayNode(), it.next().getNode());
        assertEquals(JsonNodeFactory.instance.objectNode(), it.next().getNode());
        assertEquals(JsonNodeFactory.instance.nullNode(), it.next().getNode());
        assertEquals(JsonNodeFactory.instance.numberNode(42), it.next().getNode());
        assertEquals(JsonNodeFactory.instance.objectNode().put("a", "b"), it.next().getNode());
        assertEquals(JsonNodeFactory.instance.textNode("42"), it.next().getNode());
    }

    @Test
    void testReadsDataFromZip() throws Exception {
        ByteArrayOutputStream zippedData = new ByteArrayOutputStream();

        ZipOutputStream zip = new ZipOutputStream(zippedData);
        zip.putNextEntry(new ZipEntry("file1.js"));
        zip.write("{\"a\": 42}".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.putNextEntry(new ZipEntry("file2.js"));
        zip.write("[1, 2, true]".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.flush();
        zip.close();

        API api = API.of(new InputStreamArchive("data.zip", () -> new ByteArrayInputStream(zippedData.toByteArray())))
                .build();

        @SuppressWarnings("unchecked")
        TestAnalyzer analyzer = new TestAnalyzer(mock(JacksonApiAnalyzer.class), api, Pattern.compile(".*"),
                new ObjectMapper(), StandardCharsets.UTF_8);

        BaseElementForest<TestElement> forest = analyzer.analyze(TreeFilter.matchAndDescend());

        assertEquals(2, forest.getRoots().size());

        Iterator<TestElement> it = forest.getRoots().iterator();

        assertEquals(JsonNodeFactory.instance.objectNode().put("a", 42), it.next().getNode());
        assertEquals(JsonNodeFactory.instance.arrayNode().add(1).add(2).add(true), it.next().getNode());
    }

    private static final class TestElement extends JacksonElement<TestElement> {
        public TestElement(API api, Archive archive, String filePath, TreeNode node, String key) {
            super(api, archive, filePath, node, key);
        }

        public TestElement(API api, Archive archive, String filePath, TreeNode node, int index) {
            super(api, archive, filePath, node, index);
        }
    }

    private static final class TestAnalyzer extends JacksonArchiveAnalyzer<TestElement> {

        TestAnalyzer(JacksonApiAnalyzer<TestElement> apiAnalyzer, API api, Pattern pathMatcher,
                ObjectMapper objectMapper, Charset charset) {
            super(apiAnalyzer, api, pathMatcher, objectMapper, charset);
        }

        @Override
        protected TestElement toElement(Archive archive, String filePath, TreeNode node, String keyInParent) {
            return new TestElement(getApi(), archive, filePath, node, keyInParent);
        }

        @Override
        protected TestElement toElement(Archive archive, String filePath, TreeNode node, int indexInParent) {
            return new TestElement(getApi(), archive, filePath, node, indexInParent);
        }
    }
}
