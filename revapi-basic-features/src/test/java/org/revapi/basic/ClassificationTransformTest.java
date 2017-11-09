/*
 * Copyright 2014-2017 Lukas Krejci
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
package org.revapi.basic;

import static org.revapi.basic.Util.getAnalysisContextFromFullConfig;

import java.util.Collections;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.CompatibilityType;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.Element;
import org.revapi.simple.SimpleElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class ClassificationTransformTest {

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

        @Nullable
        @Override
        public Archive getArchive() {
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

    private static API emptyAPI() {
        return new API(Collections.<Archive>emptyList(), Collections.<Archive>emptyList());
    }

    @Test
    public void test() throws Exception {
        DummyElement oldE = new DummyElement("old");
        DummyElement newE = new DummyElement("new");

        Difference difference = Difference.builder().withCode("code").addClassification(
            CompatibilityType.BINARY, DifferenceSeverity.NON_BREAKING).addClassification(CompatibilityType.SOURCE,
            DifferenceSeverity.POTENTIALLY_BREAKING).build();

        AnalysisContext config = getAnalysisContextFromFullConfig(ClassificationTransform.class,
                "[{\"extension\": \"revapi.reclassify\", \"configuration\":[{\"code\":\"code\", \"classify\": {\"BINARY\" : \"BREAKING\"}}]}]");

        try (ClassificationTransform t = new ClassificationTransform()) {
            t.initialize(config);
            difference = t.transform(oldE, newE, difference);
            assert difference != null &&
                difference.classification.get(CompatibilityType.BINARY) == DifferenceSeverity.BREAKING;
            assert difference != null &&
                difference.classification.get(CompatibilityType.SOURCE) == DifferenceSeverity.POTENTIALLY_BREAKING;
        }
    }

    //TODO add schema tests
}
