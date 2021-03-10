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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Test;
import org.revapi.API;
import org.revapi.Archive;
import org.revapi.ElementForest;
import org.revapi.TreeFilter;

class ZipArchiveAnalyzerTest {

    private static final byte[] ZIPPED_DATA;
    private static final byte[] UNCOMPRESSED_DATA;
    static {
        try {
            UNCOMPRESSED_DATA = "kachny".getBytes(UTF_8);

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ZipOutputStream out = new ZipOutputStream(bytes);
            ZipEntry data = new ZipEntry("data");
            out.putNextEntry(data);
            out.write(UNCOMPRESSED_DATA);
            out.closeEntry();

            data = new ZipEntry("other-data");
            out.putNextEntry(data);
            out.write(42);
            out.closeEntry();

            data = new ZipEntry("data/data");
            out.putNextEntry(data);
            out.write(UNCOMPRESSED_DATA);
            out.closeEntry();

            out.close();

            ZIPPED_DATA = bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize test data.", e);
        }
    }

    @Test
    void testCanFilterInZips() {
        API api = API.of(new InputStreamArchive("data", () -> new ByteArrayInputStream(ZIPPED_DATA))).build();
        DummyZipArchiveAnalyzer an = new DummyZipArchiveAnalyzer(api, singletonList(Pattern.compile("data")));

        ElementForest<DataElement> forest = an.analyze(TreeFilter.matchAndDescend());
        assertEquals(1, forest.getRoots().size());

        assertArrayEquals(UNCOMPRESSED_DATA, forest.getRoots().first().data);
    }

    @Test
    void testFallsBackToReadingFullArchive() {
        API api = API.of(new InputStreamArchive("data", () -> new ByteArrayInputStream(UNCOMPRESSED_DATA))).build();
        DummyZipArchiveAnalyzer an = new DummyZipArchiveAnalyzer(api, emptyList());

        ElementForest<DataElement> forest = an.analyze(TreeFilter.matchAndDescend());
        assertEquals(1, forest.getRoots().size());

        assertArrayEquals(UNCOMPRESSED_DATA, forest.getRoots().first().data);
    }

    private static final class DummyZipArchiveAnalyzer
            extends ZipArchiveAnalyzer<BaseElementForest<DataElement>, DataElement> {
        public DummyZipArchiveAnalyzer(API api, List<Pattern> matchPatterns) {
            super(null, api, matchPatterns);
        }

        @Override
        protected BaseElementForest<DataElement> newElementForest() {
            return new BaseElementForest<>(null);
        }

        @Override
        protected Set<DataElement> createElements(Archive a, @Nullable ZipEntry entry, InputStream data)
                throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int c;
            while ((c = data.read()) >= 0) {
                out.write(c);
            }
            return singleton(new DataElement(out.toByteArray()));
        }
    }

    private static final class DataElement extends BaseElement<DataElement> {
        private final byte[] data;

        private DataElement(byte[] data) {
            super(null);
            this.data = data;
        }

        @Override
        public int compareTo(DataElement o) {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DataElement that = (DataElement) o;
            return Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }
    }
}
