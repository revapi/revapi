package org.revapi.basic;

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
                    Collections.emptyList());

    private static final Difference POTENTIALLY_BREAKING =
            new Difference("potentiallyBreaking", "potentiallyBreaking", "blah", CompatibilityType.OTHER,
                    DifferenceSeverity.POTENTIALLY_BREAKING,
                    Collections.emptyList());

    private static final Difference BREAKING =
            new Difference("breaking", "breaking", "blah", CompatibilityType.OTHER,
                    DifferenceSeverity.BREAKING,
                    Collections.emptyList());

    @Test
    public void testDisabledByDefault() {
        DifferenceTransform<?> tr = getTestTransform("0.0.0", "0.0.1", "{}");
        Assert.assertSame(NON_BREAKING, tr.transform(null, null, NON_BREAKING));
        Assert.assertSame(POTENTIALLY_BREAKING, tr.transform(null, null, POTENTIALLY_BREAKING));
        Assert.assertSame(BREAKING, tr.transform(null, null, BREAKING));
    }

    @Test
    public void testDefaultSeverities() {
        String config = "{\"revapi\": {\"semver\": {\"ignore\": {\"enabled\": true}}}}";
        
        DifferenceTransform<?> tr = getTestTransform("0.0.0", "0.0.1", config);
        Assert.assertNull(tr.transform(null, null, NON_BREAKING));
        Assert.assertTrue(isBreaking(tr.transform(null, null, POTENTIALLY_BREAKING)));
        Assert.assertTrue(isBreaking(tr.transform(null, null, BREAKING)));

        tr = getTestTransform("0.0.0", "0.1.0", config);
        Assert.assertNull(tr.transform(null, null, NON_BREAKING));
        Assert.assertNull(tr.transform(null, null, POTENTIALLY_BREAKING));
        Assert.assertNull(tr.transform(null, null, BREAKING));

        tr = getTestTransform("0.0.0", "1.0.0", config);
        Assert.assertNull(tr.transform(null, null, NON_BREAKING));
        Assert.assertNull(tr.transform(null, null, POTENTIALLY_BREAKING));
        Assert.assertNull(tr.transform(null, null, BREAKING));

        tr = getTestTransform("1.0.0", "1.0.1", config);
        Assert.assertTrue(isBreaking(tr.transform(null, null, NON_BREAKING)));
        Assert.assertTrue(isBreaking(tr.transform(null, null, POTENTIALLY_BREAKING)));
        Assert.assertTrue(isBreaking(tr.transform(null, null, BREAKING)));

        tr = getTestTransform("1.0.0", "1.1.0", config);
        Assert.assertNull(tr.transform(null, null, NON_BREAKING));
        Assert.assertTrue(isBreaking(tr.transform(null, null, POTENTIALLY_BREAKING)));
        Assert.assertTrue(isBreaking(tr.transform(null, null, BREAKING)));

        tr = getTestTransform("1.0.0", "2.0.0", config);
        Assert.assertNull(tr.transform(null, null, NON_BREAKING));
        Assert.assertNull(tr.transform(null, null, POTENTIALLY_BREAKING));
        Assert.assertNull(tr.transform(null, null, BREAKING));
    }

    @Test
    public void testSeverityOverrides() {
        String config = "{\"revapi\": {\"semver\": {\"ignore\": {\"enabled\": true," +
                "\"changeIncreaseAllows\":{\"major\":\"potentiallyBreaking\",\"minor\":\"nonBreaking\",\"patch\": \"none\"}}}}}";


        DifferenceTransform<?> tr = getTestTransform("0.0.0", "0.0.1", config);
        Assert.assertTrue(isBreaking(tr.transform(null, null, NON_BREAKING)));
        Assert.assertTrue(isBreaking(tr.transform(null, null, POTENTIALLY_BREAKING)));
        Assert.assertTrue(isBreaking(tr.transform(null, null, BREAKING)));

        tr = getTestTransform("0.0.0", "0.1.0", config);
        Assert.assertNull(tr.transform(null, null, NON_BREAKING));
        Assert.assertTrue(isBreaking(tr.transform(null, null, POTENTIALLY_BREAKING)));
        Assert.assertTrue(isBreaking(tr.transform(null, null, BREAKING)));

        tr = getTestTransform("0.0.0", "1.0.0", config);
        Assert.assertNull(tr.transform(null, null, NON_BREAKING));
        Assert.assertNull(tr.transform(null, null, POTENTIALLY_BREAKING));
        Assert.assertTrue(isBreaking(tr.transform(null, null, BREAKING)));

        tr = getTestTransform("1.0.0", "1.0.1", config);
        Assert.assertTrue(isBreaking(tr.transform(null, null, NON_BREAKING)));
        Assert.assertTrue(isBreaking(tr.transform(null, null, POTENTIALLY_BREAKING)));
        Assert.assertTrue(isBreaking(tr.transform(null, null, BREAKING)));

        tr = getTestTransform("1.0.0", "1.1.0", config);
        Assert.assertNull(tr.transform(null, null, NON_BREAKING));
        Assert.assertTrue(isBreaking(tr.transform(null, null, POTENTIALLY_BREAKING)));
        Assert.assertTrue(isBreaking(tr.transform(null, null, BREAKING)));

        tr = getTestTransform("1.0.0", "2.0.0", config);
        Assert.assertNull(tr.transform(null, null, NON_BREAKING));
        Assert.assertNull(tr.transform(null, null, POTENTIALLY_BREAKING));
        Assert.assertTrue(isBreaking(tr.transform(null, null, BREAKING)));
    }

    private boolean isBreaking(Difference difference) {
        return difference.classification.values().stream().anyMatch(ds -> ds == DifferenceSeverity.BREAKING);
    }

    private DifferenceTransform<Element> getTestTransform(String oldVersion, String newVersion,
                                                          String configuration) {
        AnalysisContext ctx = AnalysisContext.builder()
                .withConfigurationFromJSON(configuration)
                .withOldAPI(API.of(new Ar(oldVersion)).build())
                .withNewAPI(API.of(new Ar(newVersion)).build())
                .build();

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
