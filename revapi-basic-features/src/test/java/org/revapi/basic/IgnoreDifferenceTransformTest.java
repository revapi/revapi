/*
 * Copyright 2014 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.revapi.basic;

import javax.annotation.Nonnull;

import org.junit.Assert;
import org.junit.Test;

import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.Element;
import org.revapi.simple.SimpleElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class IgnoreDifferenceTransformTest {

    private static class DummyElement extends SimpleElement {

        private final String name;

        public DummyElement(String name) {
            this.name = name;
        }

        @Nonnull
        @Override
        @SuppressWarnings("ConstantConditions")
        public API getApi() {
            return null;
        }

        @Nonnull
        @Override
        public String getFullHumanReadableString() {
            return name;
        }

        @Override
        public int compareTo(@Nonnull Element o) {
            if (!(o instanceof DummyElement)) {
                return -1;
            }

            return name.compareTo(((DummyElement) o).name);
        }
    }

    @Test
    public void testSimpleTextMatch() throws Exception {
        DummyElement oldE = new DummyElement("a");
        DummyElement newE = new DummyElement("b");

        Difference difference = Difference.builder().withCode("c").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {

            AnalysisContext config = AnalysisContext.builder()
                .withConfigurationFromJSON("{\"revapi\":{\"ignore\":[{\"code\":\"c\"}]}}").build();

            t.initialize(config);
            difference = t.transform(oldE, newE, difference);
            Assert.assertNull(difference);
        }
    }

    @Test
    public void testRegexMatch() throws Exception {
        DummyElement oldE = new DummyElement("a");
        DummyElement newE = new DummyElement("b");

        Difference difference = Difference.builder().withCode("c").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {
            AnalysisContext config = AnalysisContext.builder()
                .withConfigurationFromJSON("{\"revapi\":{\"ignore\":[{\"regex\": true, \"code\":\"c\"}]}}").build();

            t.initialize(config);
            difference = t.transform(oldE, newE, difference);
            Assert.assertNull(difference);
        }
    }

    //TODO add schema tests

    //TODO add tests for old and new element matching
}
