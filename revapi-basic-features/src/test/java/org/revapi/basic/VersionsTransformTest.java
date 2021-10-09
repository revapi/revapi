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
package org.revapi.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.revapi.CompatibilityType.BINARY;
import static org.revapi.CompatibilityType.OTHER;
import static org.revapi.CompatibilityType.SOURCE;
import static org.revapi.DifferenceSeverity.BREAKING;
import static org.revapi.DifferenceSeverity.EQUIVALENT;
import static org.revapi.DifferenceSeverity.NON_BREAKING;
import static org.revapi.DifferenceSeverity.POTENTIALLY_BREAKING;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.Criticality;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.Reference;
import org.revapi.TransformationResult;
import org.revapi.base.BaseElement;

class VersionsTransformTest {
    @Test
    void matchesByMaxSeverity() {
        shouldAllowPatchIncrease("{\"severity\": \"POTENTIALLY_BREAKING\"}",
                bld -> bld.addClassification(SOURCE, DifferenceSeverity.NON_BREAKING));
        shouldDisallowPatchIncrease("{\"severity\": \"NONE\"}",
                bld -> bld.addClassification(SOURCE, DifferenceSeverity.EQUIVALENT));
    }

    @Test
    void matchesByCriticality() {
        shouldAllowPatchIncrease("{\"criticality\": \"documented\"}", bld -> bld.withCriticality(Criticality.ALLOWED));
        shouldDisallowPatchIncrease("{\"criticality\": \"allowed\"}",
                bld -> bld.withCriticality(Criticality.DOCUMENTED));
    }

    @Test
    void matchesByCode() {
        shouldAllowPatchIncrease("{\"code\": \"allowed\"}", bld -> bld.withCode("allowed"));
        shouldAllowPatchIncrease("{\"regex\": true, \"code\": \"^allow.d\"}", bld -> bld.withCode("allowed"));
        shouldDisallowPatchIncrease("{\"code\": \"allowed\"}", bld -> bld.withCode("not-allowed"));
        shouldDisallowPatchIncrease("{\"regex\": true, \"code\": \"^allow.d\"}", bld -> bld.withCode("not-allowed"));
    }

    @Test
    void matchesByJustification() {
        shouldAllowPatchIncrease("{\"justification\": \"this is a mandatory change\"}",
                bld -> bld.withJustification("this is a mandatory change"));
        shouldAllowPatchIncrease("{\"regex\": true, \"justification\": \".* APPROVED$\"}",
                bld -> bld.withJustification("My manager considers this change ok. Manager: APPROVED"));
        shouldDisallowPatchIncrease("{\"justification\": \"this is a mandatory change\"}",
                bld -> bld.withJustification("some other justification"));
        shouldDisallowPatchIncrease("{\"regex\": true, \"justification\": \".* APPROVED$\"}",
                bld -> bld.withJustification("not approved"));
    }

    @Test
    void matchesByAttachments() {
        shouldAllowPatchIncrease("{\"attachments\": {\"a\": \"b\"}}",
                bld -> bld.addAttachment("a", "b").addAttachment("c", "d"));
        shouldDisallowPatchIncrease("{\"attachments\": {\"a\": \"b\"}}", bld -> bld.addAttachment("c", "d"));
    }

    @Test
    void matchesByClassification() {
        shouldAllowPatchIncrease("{\"classification\": {\"SOURCE\": \"BREAKING\"}}",
                bld -> bld.addClassification(SOURCE, POTENTIALLY_BREAKING).addClassification(BINARY, NON_BREAKING));
        shouldDisallowPatchIncrease("{\"classification\": {\"SOURCE\": \"NON_BREAKING\"}}",
                bld -> bld.addClassification(SOURCE, POTENTIALLY_BREAKING));
    }

    @Test
    void matchesBySuffix() {
        Archive oldar = archive("ar", "1.0.0.beta");
        Archive newar = archive("ar", "1.0.0.final");

        API oldAPI = API.of(oldar).build();
        API newAPI = API.of(newar).build();

        TestElement oldEl = new TestElement(oldAPI, oldar);
        TestElement newEl = new TestElement(newAPI, newar);

        AnalysisContext ctx = Util.setAnalysisContextFullConfig(
                AnalysisContext.builder().withOldAPI(oldAPI).withNewAPI(newAPI), VersionsTransform.class,
                "[{\"extension\": \"revapi.versions\", \"configuration\": {\"enabled\": true, "
                        + "\"versionIncreaseAllows\": {\"suffix\": {\"old\": \"beta\", \"new\": \"final\", "
                        + "\"severity\": \"NONE\"}}}}]");

        VersionsTransform<TestElement> transform = new VersionsTransform<>();
        transform.initialize(ctx);

        TransformationResult res = transform.tryTransform(oldEl, newEl,
                Difference.builder().withCode("c").withName("n").addClassification(SOURCE, EQUIVALENT).build());

        assertSame(TransformationResult.Resolution.REPLACE, res.getResolution());

        assertEquals("true", res.getDifferences().iterator().next().attachments.get("breaksVersioningRules"));
        assertEquals(Criticality.ERROR, res.getDifferences().iterator().next().criticality);
    }

    @Test
    void matchesByEmptySuffix() {
        Archive oldar = archive("ar", "1.0.0.beta");
        Archive newar = archive("ar", "1.0.0");

        API oldAPI = API.of(oldar).build();
        API newAPI = API.of(newar).build();

        TestElement oldEl = new TestElement(oldAPI, oldar);
        TestElement newEl = new TestElement(newAPI, newar);

        AnalysisContext ctx = Util.setAnalysisContextFullConfig(
                AnalysisContext.builder().withOldAPI(oldAPI).withNewAPI(newAPI), VersionsTransform.class,
                "[{\"extension\": \"revapi.versions\", \"configuration\": {\"enabled\": true, "
                        + "\"versionIncreaseAllows\": {\"suffix\": {\"old\": \"beta\", "
                        + "\"severity\": \"NONE\"}}}}]");

        VersionsTransform<TestElement> transform = new VersionsTransform<>();
        transform.initialize(ctx);

        TransformationResult res = transform.tryTransform(oldEl, newEl,
                Difference.builder().withCode("c").withName("n").addClassification(SOURCE, EQUIVALENT).build());

        assertSame(TransformationResult.Resolution.REPLACE, res.getResolution());

        assertEquals("true", res.getDifferences().iterator().next().attachments.get("breaksVersioningRules"));
        assertEquals(Criticality.ERROR, res.getDifferences().iterator().next().criticality);
    }

    @Test
    void operatesOnAffectedPrimaryAPI() {
        Archive.Versioned oldDep = archive("dep", "1");
        Archive.Versioned newDep = archive("dep", "2");
        Archive.Versioned oldMain = archive("main", "1.0.1");
        Archive.Versioned newMain = archive("main", "1.0.2");

        API oldAPI = API.of(oldMain).supportedBy(oldDep).build();
        API newAPI = API.of(newMain).supportedBy(newDep).build();

        TestElement oldDepEl = new TestElement(oldAPI, oldDep);
        TestElement newDepEl = new TestElement(newAPI, newDep);
        TestElement notAffectingOldDepEl = new TestElement(oldAPI, oldDep);
        TestElement notAffectingNewDepEl = new TestElement(newAPI, newDep);

        TestElement oldMainEl = new TestElement(oldAPI, oldMain);
        TestElement newMainEl = new TestElement(newAPI, newMain);

        oldMainEl.getReferencedElements().add(new Reference<>(oldDepEl, TestReferenceType.REF));
        newMainEl.getReferencedElements().add(new Reference<>(newDepEl, TestReferenceType.REF));

        VersionsTransform<TestElement> transform = new VersionsTransform<>();

        AnalysisContext ctx = Util.setAnalysisContextFullConfig(
                AnalysisContext.builder().withOldAPI(oldAPI).withNewAPI(newAPI), VersionsTransform.class,
                "[{\"extension\": \"revapi.versions\", \"configuration\": {\"enabled\": true}}]");

        transform.initialize(ctx);

        // the potentially breaking change from a dependency (between version 1 and 2 of the dep) still should cause
        // a breakage of semantic versioning, because this change bubbles up to the primary API which only changed its
        // patch version.
        TransformationResult res = transform.tryTransform(oldDepEl, newDepEl, Difference.builder().withCode("code")
                .withName("code").addClassification(OTHER, POTENTIALLY_BREAKING).build());

        assertEquals("true", res.getDifferences().iterator().next().attachments.get("breaksVersioningRules"));
        assertEquals(Criticality.ERROR, res.getDifferences().iterator().next().criticality);

        // now try the same with the not affecting element from the deps.. I.e. there is a breaking change in the deps
        // but it doesn't influence anything in the main API. This theoretically should never actually happen, because
        // the API changes from the deps are only "dragged" into the API by use in the primary API, but we cannot assume
        // that at this level, because that is dependent on the API analyzer. Nevertheless, we can state that we don't
        // know whether the change has an effect on the API of the primary archives.
        res = transform.tryTransform(notAffectingOldDepEl, notAffectingNewDepEl,
                Difference.builder().withCode("code").withName("code").addClassification(OTHER, BREAKING).build());

        assertEquals("unknown", res.getDifferences().iterator().next().attachments.get("breaksVersioningRules"));
        assertNull(res.getDifferences().iterator().next().criticality);

        // now, a final test. Let's make the non-affecting element a child of the depEl. Now there is a chain to
        // the main API through such parent, and therefore we should get a breakage when transforming the non-affected
        // element now
        oldDepEl.getChildren().add(notAffectingOldDepEl);
        newDepEl.getChildren().add(notAffectingNewDepEl);
        res = transform.tryTransform(notAffectingOldDepEl, notAffectingNewDepEl,
                Difference.builder().withCode("code").withName("code").addClassification(OTHER, BREAKING).build());

        assertEquals("true", res.getDifferences().iterator().next().attachments.get("breaksVersioningRules"));
        assertEquals(Criticality.ERROR, res.getDifferences().iterator().next().criticality);
    }

    @Test
    void modifiesClassification() {
        TransformRun r = transform(
                "{\"enabled\": true, \"onAllowed\": {\"classification\": {\"OTHER\": \"BREAKING\"}}}", bld -> {
                });

        assertSame(BREAKING, r.result.getDifferences().iterator().next().classification.get(OTHER));
    }

    @Test
    void modifiesDescription() {
        TransformRun r = transform("{\"enabled\": true, \"onAllowed\": {\"description\": \"kachny\"}}", bld -> {
        });

        assertEquals("kachny", r.result.getDifferences().iterator().next().description);

        r = transform("{\"enabled\": true, \"onAllowed\": {\"description\": {\"prepend\": \"very \"}}}",
                bld -> bld.withDescription("kachny"));

        assertEquals("very kachny", r.result.getDifferences().iterator().next().description);

        r = transform("{\"enabled\": true, \"onAllowed\": {\"description\": {\"append\": \" much\"}}}",
                bld -> bld.withDescription("kachny"));

        assertEquals("kachny much", r.result.getDifferences().iterator().next().description);
    }

    @Test
    void modifiesJustification() {
        TransformRun r = transform("{\"enabled\": true, \"onAllowed\": {\"justification\": \"kachny\"}}", bld -> {
        });

        assertEquals("kachny", r.result.getDifferences().iterator().next().justification);

        r = transform("{\"enabled\": true, \"onAllowed\": {\"justification\": {\"prepend\": \"very \"}}}",
                bld -> bld.withJustification("kachny"));

        assertEquals("very kachny", r.result.getDifferences().iterator().next().justification);

        r = transform("{\"enabled\": true, \"onAllowed\": {\"justification\": {\"append\": \" much\"}}}",
                bld -> bld.withJustification("kachny"));

        assertEquals("kachny much", r.result.getDifferences().iterator().next().justification);
    }

    @Test
    void modifiesAttachments() {
        TransformRun r = transform("{\"enabled\": true, \"onAllowed\": {\"attachments\": {\"a\": \"b\"}}}", bld -> {
        });

        assertEquals("b", r.result.getDifferences().iterator().next().attachments.get("a"));
    }

    @Test
    void modifiesCriticality() {
        TransformRun r = transform("{\"enabled\": true, \"onAllowed\": {\"criticality\": \"documented\"}}", bld -> {
        });

        assertEquals(Criticality.DOCUMENTED, r.result.getDifferences().iterator().next().criticality);
    }

    @Test
    void modifiesByRemoval() {
        TransformRun r = transform("{\"enabled\": true, \"onAllowed\": {\"remove\": true}}", bld -> {
        });

        assertSame(TransformationResult.Resolution.DISCARD, r.result.getResolution());
    }

    static TransformRun transform(String config, Consumer<Difference.Builder> difference) {
        TestSetup ts = new TestSetup("1.0.0", "1.0.1", config);

        Difference.Builder bld = Difference.builder().withCode("c").withName("n");

        difference.accept(bld);

        Difference origDiff = bld.build();

        return new TransformRun(ts, origDiff, ts.transform.tryTransform(ts.oldElement, ts.newElement, origDiff));
    }

    static void shouldAllowPatchIncrease(String patchConfig, Consumer<Difference.Builder> difference) {
        TransformRun r = transform("{\"enabled\": true, \"versionIncreaseAllows\": {\"patch\":  " + patchConfig + "}}",
                difference);

        Difference origDiff = r.originalDifference;
        TransformationResult res = r.result;

        String expectedCode = origDiff.code;
        String expectedName = origDiff.name;

        assertSame(TransformationResult.Resolution.REPLACE, res.getResolution());
        assertEquals(1, res.getDifferences().size());
        Difference diff = res.getDifferences().iterator().next();
        assertEquals(origDiff.criticality, diff.criticality);
        assertEquals(expectedCode, diff.code);
        assertEquals(expectedName, diff.name);
        assertEquals("false", diff.attachments.get("breaksVersioningRules"));

        testTransformationIdempotent(r.testSetup, res);
    }

    static void shouldDisallowPatchIncrease(String patchConfig, Consumer<Difference.Builder> difference) {
        TransformRun r = transform("{\"enabled\": true, \"versionIncreaseAllows\": {\"patch\":  " + patchConfig + "}}",
                difference);

        Difference origDiff = r.originalDifference;
        TransformationResult res = r.result;

        String expectedCode = origDiff.code;
        String expectedName = origDiff.name;

        assertSame(TransformationResult.Resolution.REPLACE, res.getResolution());
        assertEquals(1, res.getDifferences().size());
        Difference diff = res.getDifferences().iterator().next();
        assertEquals(Criticality.ERROR, diff.criticality);
        assertEquals(expectedCode, diff.code);
        assertEquals(expectedName, diff.name);
        assertEquals("true", diff.attachments.get("breaksVersioningRules"));

        testTransformationIdempotent(r.testSetup, res);
    }

    static void testTransformationIdempotent(TestSetup ts, TransformationResult originalResult) {
        Difference origDiff = originalResult.getDifferences().iterator().next();

        TransformationResult newResult = ts.transform.tryTransform(ts.oldElement, ts.newElement, origDiff);

        assertSame(TransformationResult.Resolution.KEEP, newResult.getResolution());
    }

    static Archive.Versioned archive(String baseName, String version) {
        Archive.Versioned ret = mock(Archive.Versioned.class, baseName + "@" + version);
        when(ret.getBaseName()).thenReturn(baseName);
        when(ret.getVersion()).thenReturn(version);
        return ret;
    }

    static final class TestElement extends BaseElement<TestElement> {
        private TestElement(API api, @Nullable Archive archive) {
            super(api, archive);
        }

        @Override
        public int compareTo(TestElement o) {
            return 0;
        }
    }

    static final class TestReferenceType implements Reference.Type<TestElement> {
        static final TestReferenceType REF = new TestReferenceType();

        @Override
        public String getName() {
            return "ref";
        }
    }

    static final class TransformRun {
        final TestSetup testSetup;
        final Difference originalDifference;
        final TransformationResult result;

        TransformRun(TestSetup testSetup, Difference originalDifference, TransformationResult result) {
            this.testSetup = testSetup;
            this.originalDifference = originalDifference;
            this.result = result;
        }
    }

    static final class TestSetup {
        Archive.Versioned oldArchive;
        Archive.Versioned newArchive;
        TestElement oldElement;
        TestElement newElement;
        VersionsTransform<TestElement> transform;

        TestSetup(String oldVersion, String newVersion, String transformConfig) {
            this("ar", oldVersion, "ar", newVersion, transformConfig);
        }

        TestSetup(String oldBaseName, String oldVersion, String newBaseName, String newVersion,
                String transformConfig) {
            oldArchive = archive(oldBaseName, oldVersion);
            newArchive = archive(newBaseName, newVersion);

            API oldAPI = API.of(oldArchive).build();
            API newAPI = API.of(newArchive).build();

            oldElement = new TestElement(oldAPI, oldArchive);
            newElement = new TestElement(newAPI, newArchive);

            AnalysisContext ctx = Util.setAnalysisContextFullConfig(
                    AnalysisContext.builder().withOldAPI(oldAPI).withNewAPI(newAPI), VersionsTransform.class,
                    "[{\"extension\": \"revapi.versions\", \"configuration\": " + transformConfig + "}]");

            transform = new VersionsTransform<>();

            transform.initialize(ctx);
        }
    }
}
