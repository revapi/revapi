/*
 * Copyright 2014-2018 Lukas Krejci
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

import static org.revapi.basic.Util.transformAndAssumeOne;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import javax.annotation.Nonnull;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.CompatibilityType;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.DifferenceTransform;
import org.revapi.Element;

/**
 * @author Lukas Krejci
 * @since 0.4.5
 */
public class SemverIgnoreTransformTest {

    private static final Difference NON_BREAKING =
            new Difference("nonBreaking", "nonBrekaing", "blah", CompatibilityType.OTHER,
                    DifferenceSeverity.NON_BREAKING,
                    Collections.emptyMap());

    private static final Difference POTENTIALLY_BREAKING =
            new Difference("potentiallyBreaking", "potentiallyBreaking", "blah", CompatibilityType.OTHER,
                    DifferenceSeverity.POTENTIALLY_BREAKING,
                    Collections.emptyMap());

    private static final Difference BREAKING =
            new Difference("breaking", "breaking", "blah", CompatibilityType.OTHER,
                    DifferenceSeverity.BREAKING,
                    Collections.emptyMap());

    @Test
    public void testDisabledByDefault() {
        DifferenceTransform<?> tr = getTestTransform("0.0.0", "0.0.1", "[{\"extension\": \"revapi.semver.ignore\", \"configuration\": {}}]");
        Assert.assertSame(NON_BREAKING, transformAndAssumeOne(tr, null, null, NON_BREAKING));
        Assert.assertSame(POTENTIALLY_BREAKING, transformAndAssumeOne(tr, null, null, POTENTIALLY_BREAKING));
        Assert.assertSame(BREAKING, transformAndAssumeOne(tr, null, null, BREAKING));
    }

    @Test
    public void testDefaultSeverities() {
        String config = "[{\"extension\": \"revapi.semver.ignore\", \"configuration\": {\"enabled\": true}}]";

        DifferenceTransform<?> tr = getTestTransform("0.0.0", "0.0.1", config);
        Assert.assertNull(transformAndAssumeOne(tr, null, null, NON_BREAKING));
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, POTENTIALLY_BREAKING)));
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, BREAKING)));

        tr = getTestTransform("0.0.0", "0.1.0", config);
        Assert.assertNull(transformAndAssumeOne(tr, null, null, NON_BREAKING));
        Assert.assertNull(transformAndAssumeOne(tr, null, null, POTENTIALLY_BREAKING));
        Assert.assertNull(transformAndAssumeOne(tr, null, null, BREAKING));

        tr = getTestTransform("0.0.0", "1.0.0", config);
        Assert.assertNull(transformAndAssumeOne(tr, null, null, NON_BREAKING));
        Assert.assertNull(transformAndAssumeOne(tr, null, null, POTENTIALLY_BREAKING));
        Assert.assertNull(transformAndAssumeOne(tr, null, null, BREAKING));

        tr = getTestTransform("1.0.0", "1.0.1", config);
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, NON_BREAKING)));
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, POTENTIALLY_BREAKING)));
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, BREAKING)));

        tr = getTestTransform("1.0.0", "1.1.0", config);
        Assert.assertNull(transformAndAssumeOne(tr, null, null, NON_BREAKING));
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, POTENTIALLY_BREAKING)));
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, BREAKING)));

        tr = getTestTransform("1.0.0", "2.0.0", config);
        Assert.assertNull(transformAndAssumeOne(tr, null, null, NON_BREAKING));
        Assert.assertNull(transformAndAssumeOne(tr, null, null, POTENTIALLY_BREAKING));
        Assert.assertNull(transformAndAssumeOne(tr, null, null, BREAKING));
    }

    @Test
    public void testSeverityOverrides() {
        String config = "[{\"extension\": \"revapi.semver.ignore\", \"configuration\": {\"enabled\": true," +
                "\"versionIncreaseAllows\":{\"major\":\"potentiallyBreaking\",\"minor\":\"nonBreaking\",\"patch\": \"none\"}}}]";


        DifferenceTransform<?> tr = getTestTransform("0.0.0", "0.0.1", config);
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, NON_BREAKING)));
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, POTENTIALLY_BREAKING)));
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, BREAKING)));

        tr = getTestTransform("0.0.0", "0.1.0", config);
        Assert.assertNull(transformAndAssumeOne(tr, null, null, NON_BREAKING));
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, POTENTIALLY_BREAKING)));
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, BREAKING)));

        tr = getTestTransform("0.0.0", "1.0.0", config);
        Assert.assertNull(transformAndAssumeOne(tr, null, null, NON_BREAKING));
        Assert.assertNull(transformAndAssumeOne(tr, null, null, POTENTIALLY_BREAKING));
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, BREAKING)));

        tr = getTestTransform("1.0.0", "1.0.1", config);
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, NON_BREAKING)));
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, POTENTIALLY_BREAKING)));
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, BREAKING)));

        tr = getTestTransform("1.0.0", "1.1.0", config);
        Assert.assertNull(transformAndAssumeOne(tr, null, null, NON_BREAKING));
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, POTENTIALLY_BREAKING)));
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, BREAKING)));

        tr = getTestTransform("1.0.0", "2.0.0", config);
        Assert.assertNull(transformAndAssumeOne(tr, null, null, NON_BREAKING));
        Assert.assertNull(transformAndAssumeOne(tr, null, null, POTENTIALLY_BREAKING));
        Assert.assertTrue(isBreaking(transformAndAssumeOne(tr, null, null, BREAKING)));
    }

    @Test
    public void testPassthrough() {
        String config = "[{\"extension\": \"revapi.semver.ignore\", \"configuration\": {\"enabled\": true, \"passThroughDifferences\": [\"potentiallyBreaking\"]}}]";

        DifferenceTransform<?> tr = getTestTransform("1.0.0", "2.0.0", config);

        Assert.assertNull(transformAndAssumeOne(tr, null, null, NON_BREAKING));
        Assert.assertSame(POTENTIALLY_BREAKING, transformAndAssumeOne(tr, null, null, POTENTIALLY_BREAKING));
        Assert.assertNull(transformAndAssumeOne(tr, null, null, BREAKING));
    }

    @Test
    public void testNoOldVersion() {
        DifferenceTransform<?> tr = getTestTransform(null, "15", "[{\"extension\": \"revapi.semver.ignore\", \"configuration\": {\"enabled\": true}}]");
        Assert.assertSame(NON_BREAKING, transformAndAssumeOne(tr, null, null, NON_BREAKING));
        Assert.assertSame(POTENTIALLY_BREAKING, transformAndAssumeOne(tr, null, null, POTENTIALLY_BREAKING));
        Assert.assertSame(BREAKING, transformAndAssumeOne(tr, null, null, BREAKING));
    }

    private boolean isBreaking(Difference difference) {
        return difference != null && difference.classification.values().stream().anyMatch(ds -> ds == DifferenceSeverity.BREAKING);
    }

    private DifferenceTransform<Element> getTestTransform(String oldVersion, String newVersion,
                                                          String configuration) {

        API oldApi = oldVersion != null
                ? API.of(new Ar(oldVersion)).build()
                : API.of().build();

        API newApi = newVersion != null
                ? API.of(new Ar(newVersion)).build()
                : API.of().build();

        AnalysisContext ctx = Util.setAnalysisContextFullConfig(AnalysisContext.builder()
                .withOldAPI(oldApi)
                .withNewAPI(newApi), SemverIgnoreTransform.class, configuration);

        SemverIgnoreTransform tr = new SemverIgnoreTransform();

        tr.initialize(ctx);

        return tr;
    }

    private static final class Ar implements Archive.Versioned {

        private final String version;

        private Ar(String version) {
            this.version = version;
        }

        @Nonnull @Override public String getVersion() {
            return version;
        }

        @Nonnull @Override public String getName() {
            return null;
        }

        @Nonnull @Override public InputStream openStream() throws IOException {
            return null;
        }
    }
}
