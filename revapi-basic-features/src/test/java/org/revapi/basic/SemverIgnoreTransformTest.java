/*
 * Copyright 2014-2023 Lukas Krejci
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.revapi.basic.Util.transformAndAssumeOne;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nonnull;

import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.CompatibilityType;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.base.BaseElement;

/**
 * @author Lukas Krejci
 *
 * @since 0.4.5
 */
public class SemverIgnoreTransformTest {

    private static final Difference NON_BREAKING = Difference.builder().withCode("nonBreaking").withName("nonBreaking")
            .addClassification(CompatibilityType.OTHER, DifferenceSeverity.NON_BREAKING).build();

    private static final Difference POTENTIALLY_BREAKING = Difference.builder().withCode("potentiallyBreaking")
            .withName("potentiallyBreaking")
            .addClassification(CompatibilityType.OTHER, DifferenceSeverity.POTENTIALLY_BREAKING).build();

    private static final Difference BREAKING = Difference.builder().withCode("breaking").withName("breaking")
            .addClassification(CompatibilityType.OTHER, DifferenceSeverity.BREAKING).build();

    @Test
    public void testDisabledByDefault() {
        TestSetup setup = TestSetup.of("0.0.0", "0.0.1",
                "[{\"extension\": \"revapi.semver.ignore\", \"configuration\": {}}]");
        assertSame(NON_BREAKING,
                transformAndAssumeOne(setup.transform, setup.oldElement, setup.newElement, NON_BREAKING));
        assertSame(POTENTIALLY_BREAKING,
                transformAndAssumeOne(setup.transform, setup.oldElement, setup.newElement, POTENTIALLY_BREAKING));
        assertSame(BREAKING, transformAndAssumeOne(setup.transform, setup.oldElement, setup.newElement, BREAKING));
    }

    @Test
    public void testDefaultSeverities() {
        String config = "[{\"extension\": \"revapi.semver.ignore\", \"configuration\": {\"enabled\": true}}]";

        TestSetup t = TestSetup.of("0.0.0", "0.0.1", config);
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, NON_BREAKING));
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, POTENTIALLY_BREAKING)));
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, BREAKING)));

        t = TestSetup.of("0.0.0", "0.1.0", config);
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, NON_BREAKING));
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, POTENTIALLY_BREAKING));
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, BREAKING));

        t = TestSetup.of("0.0.0", "1.0.0", config);
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, NON_BREAKING));
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, POTENTIALLY_BREAKING));
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, BREAKING));

        t = TestSetup.of("1.0.0", "1.0.1", config);
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, NON_BREAKING)));
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, POTENTIALLY_BREAKING)));
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, BREAKING)));

        t = TestSetup.of("1.0.0", "1.1.0", config);
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, NON_BREAKING));
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, POTENTIALLY_BREAKING)));
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, BREAKING)));

        t = TestSetup.of("1.0.0", "2.0.0", config);
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, NON_BREAKING));
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, POTENTIALLY_BREAKING));
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, BREAKING));
    }

    @Test
    public void testSeverityOverrides() {
        String config = "[{\"extension\": \"revapi.semver.ignore\", \"configuration\": {\"enabled\": true,"
                + "\"versionIncreaseAllows\":{\"major\":\"potentiallyBreaking\",\"minor\":\"nonBreaking\",\"patch\": \"none\"}}}]";

        TestSetup t = TestSetup.of("0.0.0", "0.0.1", config);
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, NON_BREAKING)));
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, POTENTIALLY_BREAKING)));
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, BREAKING)));

        t = TestSetup.of("0.0.0", "0.1.0", config);
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, NON_BREAKING));
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, POTENTIALLY_BREAKING)));
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, BREAKING)));

        t = TestSetup.of("0.0.0", "1.0.0", config);
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, NON_BREAKING));
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, POTENTIALLY_BREAKING));
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, BREAKING)));

        t = TestSetup.of("1.0.0", "1.0.1", config);
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, NON_BREAKING)));
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, POTENTIALLY_BREAKING)));
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, BREAKING)));

        t = TestSetup.of("1.0.0", "1.1.0", config);
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, NON_BREAKING));
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, POTENTIALLY_BREAKING)));
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, BREAKING)));

        t = TestSetup.of("1.0.0", "2.0.0", config);
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, NON_BREAKING));
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, POTENTIALLY_BREAKING));
        assertTrue(isBreaking(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, BREAKING)));
    }

    @Test
    public void testPassthrough() {
        String config = "[{\"extension\": \"revapi.semver.ignore\", \"configuration\": {\"enabled\": true, \"passThroughDifferences\": [\"potentiallyBreaking\"]}}]";

        TestSetup t = TestSetup.of("1.0.0", "2.0.0", config);

        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, NON_BREAKING));
        assertSame(POTENTIALLY_BREAKING,
                transformAndAssumeOne(t.transform, t.oldElement, t.newElement, POTENTIALLY_BREAKING));
        assertNull(transformAndAssumeOne(t.transform, t.oldElement, t.newElement, BREAKING));
    }

    @Test
    public void testNoOldVersion() {
        TestSetup t = TestSetup.of(null, "15",
                "[{\"extension\": \"revapi.semver.ignore\", \"configuration\": {\"enabled\": true}}]");

        assertSame(NON_BREAKING, transformAndAssumeOne(t.transform, null, t.newElement, NON_BREAKING));
        assertSame(POTENTIALLY_BREAKING, transformAndAssumeOne(t.transform, null, t.newElement, POTENTIALLY_BREAKING));
        assertSame(BREAKING, transformAndAssumeOne(t.transform, null, t.newElement, BREAKING));
    }

    @Test
    public void testNoNewVersion() {
        TestSetup t = TestSetup.of("1", null,
                "[{\"extension\": \"revapi.semver.ignore\", \"configuration\": {\"enabled\": true}}]");

        assertSame(NON_BREAKING, transformAndAssumeOne(t.transform, t.oldElement, null, NON_BREAKING));
        assertSame(POTENTIALLY_BREAKING, transformAndAssumeOne(t.transform, t.oldElement, null, POTENTIALLY_BREAKING));
        assertSame(BREAKING, transformAndAssumeOne(t.transform, t.oldElement, null, BREAKING));
    }

    @Test
    public void testAppliesNameAndDescriptionChangesOnlyOnce() {
        TestSetup t = TestSetup.of("1.0.0", "1.0.1",
                "[{\"extension\": \"revapi.semver.ignore\", \"configuration\": {\"enabled\": true}}]");

        Difference transformed = transformAndAssumeOne(t.transform, t.oldElement, t.newElement, POTENTIALLY_BREAKING);

        Difference transformed2 = transformAndAssumeOne(t.transform, t.oldElement, t.newElement, transformed);

        assertEquals(transformed, transformed2);
    }

    private boolean isBreaking(Difference difference) {
        return difference != null
                && difference.classification.values().stream().anyMatch(ds -> ds == DifferenceSeverity.BREAKING);
    }

    private static final class TestSetup {
        final SemverIgnoreTransform<DummyElement> transform;
        final Ar oldArchive;
        final Ar newArchive;
        final DummyElement oldElement;
        final DummyElement newElement;

        static TestSetup of(String oldArchiveVersion, String newArchiveVersion, String configuration) {
            Ar oldArchive = oldArchiveVersion == null ? null : new Ar("ar", oldArchiveVersion);
            Ar newArchive = newArchiveVersion == null ? null : new Ar("ar", newArchiveVersion);

            API oldApi = oldArchive != null ? API.of(oldArchive).build() : API.of().build();
            API newApi = newArchive != null ? API.of(newArchive).build() : API.of().build();

            AnalysisContext ctx = Util.setAnalysisContextFullConfig(
                    AnalysisContext.builder().withOldAPI(oldApi).withNewAPI(newApi), SemverIgnoreTransform.class,
                    configuration);

            SemverIgnoreTransform<DummyElement> tr = new SemverIgnoreTransform<>();

            tr.initialize(ctx);

            DummyElement oldElement = oldArchive == null ? null : new DummyElement(oldApi, oldArchive);
            DummyElement newElement = newArchive == null ? null : new DummyElement(newApi, newArchive);

            return new TestSetup(tr, oldArchive, newArchive, oldElement, newElement);
        }

        private TestSetup(SemverIgnoreTransform<DummyElement> transform, Ar oldArchive, Ar newArchive,
                DummyElement oldElement, DummyElement newElement) {
            this.transform = transform;
            this.oldArchive = oldArchive;
            this.newArchive = newArchive;
            this.oldElement = oldElement;
            this.newElement = newElement;
        }
    }

    private static final class DummyElement extends BaseElement<DummyElement> {
        public DummyElement(API api, Ar archive) {
            super(api, archive);
        }

        @Override
        public int compareTo(DummyElement o) {
            return 0;
        }
    }

    private static final class Ar implements Archive.Versioned {

        private final String version;
        private final String name;

        private Ar(String name, String version) {
            this.version = version;
            this.name = name;
        }

        @Nonnull
        @Override
        public String getVersion() {
            return version;
        }

        @Nonnull
        @Override
        public String getName() {
            return name + "@" + version;
        }

        @Override
        public String getBaseName() {
            return name;
        }

        @Nonnull
        @Override
        public InputStream openStream() throws IOException {
            return null;
        }
    }
}
