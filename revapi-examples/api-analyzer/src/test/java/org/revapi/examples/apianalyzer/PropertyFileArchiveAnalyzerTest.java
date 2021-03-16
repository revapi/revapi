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
package org.revapi.examples.apianalyzer;

import static java.nio.charset.StandardCharsets.UTF_8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.revapi.API;
import org.revapi.TreeFilter;
import org.revapi.base.BaseElementForest;
import org.revapi.base.InputStreamArchive;

class PropertyFileArchiveAnalyzerTest {

    @Test
    void loadsPropertyFile() {
        API api = API.of(new InputStreamArchive("ar", () -> new ByteArrayInputStream("a=b\nc=d".getBytes(UTF_8))))
                .build();

        PropertyFileArchiveAnalyzer analyzer = new PropertyFileArchiveAnalyzer(api, null);
        BaseElementForest<PropertyElement> forest = analyzer.analyze(TreeFilter.matchAndDescend());

        assertNotNull(forest);
        assertSame(api, forest.getApi());
        assertEquals(2, forest.getRoots().size());

        PropertyElement first = forest.getRoots().first();
        PropertyElement second = forest.getRoots().last();

        assertEquals("a", first.getKey());
        assertEquals("b", first.getValue());
        assertEquals("c", second.getKey());
        assertEquals("d", second.getValue());
    }

    @Test
    void failsOnNonPropertyFile() {
        API api = API.of(new InputStreamArchive("ar", () -> new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException();
            }
        })).build();
        PropertyFileArchiveAnalyzer analyzer = new PropertyFileArchiveAnalyzer(api, null);

        assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(TreeFilter.matchAndDescend()));
    }
}
