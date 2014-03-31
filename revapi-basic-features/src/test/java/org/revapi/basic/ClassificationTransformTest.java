package org.revapi.basic;

import java.util.Collections;

import javax.annotation.Nonnull;

import org.junit.Test;

import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.ChangeSeverity;
import org.revapi.CompatibilityType;
import org.revapi.Difference;
import org.revapi.Element;
import org.revapi.simple.SimpleElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class ClassificationTransformTest {

    private static class DummyCompatibility extends CompatibilityType {

        public static final CompatibilityType DUMMY_1 = new DummyCompatibility("dummy.1");
        public static final CompatibilityType DUMMY_2 = new DummyCompatibility("dummy.2");

        public DummyCompatibility(@Nonnull String name) {
            super(name);
        }
    }

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

    private static API emptyAPI() {
        return new API(Collections.<Archive>emptyList(), Collections.<Archive>emptyList());
    }

    @Test
    public void test() throws Exception {
        DummyElement oldE = new DummyElement("old");
        DummyElement newE = new DummyElement("new");

        Difference difference = Difference.builder().withCode("code").addClassification(
            DummyCompatibility.DUMMY_2, ChangeSeverity.NON_BREAKING).addClassification(DummyCompatibility.DUMMY_1,
            ChangeSeverity.POTENTIALLY_BREAKING).build();

        AnalysisContext config = AnalysisContext.builder()
            .withConfigurationFromJSON(
                "{\"revapi\": {\"reclassify\":[{\"code\":\"code\", \"classify\": {\"dummy.2\" : \"BREAKING\"}}]}}")
            .build();

        try (ClassificationTransform t = new ClassificationTransform()) {
            t.initialize(config);
            difference = t.transform(oldE, newE, difference);
            assert difference != null &&
                difference.classification.get(DummyCompatibility.DUMMY_2) == ChangeSeverity.BREAKING;
            assert difference != null &&
                difference.classification.get(DummyCompatibility.DUMMY_1) == ChangeSeverity.POTENTIALLY_BREAKING;
        }
    }

    //TODO add schema tests
}
