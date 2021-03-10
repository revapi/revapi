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

import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.revapi.CompatibilityType.BINARY;
import static org.revapi.CompatibilityType.OTHER;
import static org.revapi.CompatibilityType.SEMANTIC;
import static org.revapi.CompatibilityType.SOURCE;
import static org.revapi.DifferenceSeverity.BREAKING;
import static org.revapi.DifferenceSeverity.EQUIVALENT;
import static org.revapi.DifferenceSeverity.NON_BREAKING;
import static org.revapi.DifferenceSeverity.POTENTIALLY_BREAKING;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.ApiAnalyzer;
import org.revapi.Criticality;
import org.revapi.Difference;
import org.revapi.ElementMatcher;
import org.revapi.FilterFinishResult;
import org.revapi.FilterStartResult;
import org.revapi.Ternary;
import org.revapi.TransformationResult;
import org.revapi.TreeFilter;
import org.revapi.base.BaseElement;

public class DifferencesTransformTest {

    private DummyElement oldEl = mock(DummyElement.class);

    private DummyElement newEl = mock(DummyElement.class);

    private static final API EMPTY_API = API.builder().build();

    @Test
    public void testAddsJustification() {
        DifferencesTransform<DummyElement> tr = new DifferencesTransform<>();
        tr.initialize(context(JsonNodeFactory.instance.objectNode().set("differences",
                JsonNodeFactory.instance.arrayNode().add(JsonNodeFactory.instance.objectNode().put("regex", true)
                        .put("code", ".*").put("justification", "because")))));

        Difference transformed = Util.transformAndAssumeOne(tr, oldEl, newEl,
                Difference.builder().withCode("whatevs").build());

        assertNotNull(transformed);
        assertEquals("because", transformed.justification);
    }

    @Test
    public void testAddsCriticality() throws Exception {
        DifferencesTransform<DummyElement> tr = new DifferencesTransform<>();
        tr.initialize(context(JsonNodeFactory.instance.objectNode().set("differences",
                JsonNodeFactory.instance.arrayNode().add(JsonNodeFactory.instance.objectNode().put("regex", true)
                        .put("code", ".*").put("criticality", "documented")))));

        Difference transformed = Util.transformAndAssumeOne(tr, oldEl, newEl,
                Difference.builder().withCode("whatevs").build());

        assertNotNull(transformed);
        assertEquals(Criticality.DOCUMENTED, transformed.criticality);
    }

    @Test
    public void testAddsClassification() throws Exception {
        DifferencesTransform<DummyElement> tr = new DifferencesTransform<>();
        tr.initialize(context(JsonNodeFactory.instance.objectNode().set("differences",
                JsonNodeFactory.instance.arrayNode()
                        .add(JsonNodeFactory.instance.objectNode().put("regex", true).put("code", ".*").set("classify",
                                JsonNodeFactory.instance.objectNode().put("SOURCE", "BREAKING")
                                        .put("BINARY", "NON_BREAKING").put("SEMANTIC", "POTENTIALLY_BREAKING")
                                        .put("OTHER", "EQUIVALENT"))))));

        Difference transformed = Util.transformAndAssumeOne(tr, oldEl, newEl,
                Difference.builder().withCode("whatevs").build());

        assertNotNull(transformed);
        assertEquals(BREAKING, transformed.classification.get(SOURCE));
        assertEquals(NON_BREAKING, transformed.classification.get(BINARY));
        assertEquals(POTENTIALLY_BREAKING, transformed.classification.get(SEMANTIC));
        assertEquals(EQUIVALENT, transformed.classification.get(OTHER));

        transformed = Util.transformAndAssumeOne(tr, oldEl, newEl,
                Difference.builder().withCode("whatevs").addClassification(SOURCE, BREAKING)
                        .addClassification(BINARY, BREAKING).addClassification(SEMANTIC, BREAKING)
                        .addClassification(OTHER, BREAKING).build());

        assertNotNull(transformed);
        assertEquals(BREAKING, transformed.classification.get(SOURCE));
        assertEquals(NON_BREAKING, transformed.classification.get(BINARY));
        assertEquals(POTENTIALLY_BREAKING, transformed.classification.get(SEMANTIC));
        assertEquals(EQUIVALENT, transformed.classification.get(OTHER));
    }

    @Test
    public void testIgnores() throws Exception {
        DifferencesTransform<DummyElement> tr = new DifferencesTransform<>();
        tr.initialize(context(JsonNodeFactory.instance.objectNode().set("differences", JsonNodeFactory.instance
                .arrayNode()
                .add(JsonNodeFactory.instance.objectNode().put("regex", true).put("code", ".*").put("ignore", true)))));

        Difference transformed = Util.transformAndAssumeOne(tr, oldEl, newEl,
                Difference.builder().withCode("whatevs").build());

        assertNull(transformed);
    }

    @Test
    public void testAddsAttachments() throws Exception {
        DifferencesTransform<DummyElement> tr = new DifferencesTransform<>();
        tr.initialize(context(JsonNodeFactory.instance.objectNode().set("differences", JsonNodeFactory.instance
                .arrayNode()
                .add(JsonNodeFactory.instance.objectNode().put("regex", true).put("code", ".*").set("attachments",
                        JsonNodeFactory.instance.objectNode().put("attach1", "val1").put("attach2", "val2"))))));

        Difference transformed = Util.transformAndAssumeOne(tr, oldEl, newEl,
                Difference.builder().withCode("whatevs").addAttachment("attach1", "originalValue").build());

        assertNotNull(transformed);
        assertEquals("val1", transformed.attachments.get("attach1"));
        assertEquals("val2", transformed.attachments.get("attach2"));
    }

    @Test
    public void testBulkAddsJustification() throws Exception {
        DifferencesTransform<DummyElement> tr = new DifferencesTransform<>();
        tr.initialize(context(
                JsonNodeFactory.instance.objectNode().put("justification", "all of this is cool").set("differences",
                        JsonNodeFactory.instance.arrayNode()
                                .add(JsonNodeFactory.instance.objectNode().put("code", "c1"))
                                .add(JsonNodeFactory.instance.objectNode().put("code", "c2")))));

        Difference transformed = Util.transformAndAssumeOne(tr, oldEl, newEl,
                Difference.builder().withCode("c1").build());
        assertNotNull(transformed);
        assertEquals("all of this is cool", transformed.justification);

        transformed = Util.transformAndAssumeOne(tr, oldEl, newEl, Difference.builder().withCode("c2").build());
        assertNotNull(transformed);
        assertEquals("all of this is cool", transformed.justification);

        transformed = Util.transformAndAssumeOne(tr, oldEl, newEl, Difference.builder().withCode("c3").build());
        assertNotNull(transformed);
        assertNull(transformed.justification);
    }

    @Test
    public void testBulkAddsCriticality() throws Exception {
        DifferencesTransform<DummyElement> tr = new DifferencesTransform<>();
        tr.initialize(context(JsonNodeFactory.instance.objectNode().put("criticality", "highlight").set("differences",
                JsonNodeFactory.instance.arrayNode().add(JsonNodeFactory.instance.objectNode().put("code", "c1"))
                        .add(JsonNodeFactory.instance.objectNode().put("code", "c2")))));

        Difference transformed = Util.transformAndAssumeOne(tr, oldEl, newEl,
                Difference.builder().withCode("c1").build());
        assertNotNull(transformed);
        assertEquals(Criticality.HIGHLIGHT, transformed.criticality);

        transformed = Util.transformAndAssumeOne(tr, oldEl, newEl, Difference.builder().withCode("c2").build());
        assertNotNull(transformed);
        assertEquals(Criticality.HIGHLIGHT, transformed.criticality);

        transformed = Util.transformAndAssumeOne(tr, oldEl, newEl, Difference.builder().withCode("c3").build());
        assertNotNull(transformed);
        assertNull(transformed.criticality);
    }

    @Test
    public void testBulkAddsClassification() throws Exception {
        DifferencesTransform<DummyElement> tr = new DifferencesTransform<>();
        tr.initialize(context(((ObjectNode) JsonNodeFactory.instance.objectNode().set("classify",
                JsonNodeFactory.instance.objectNode().put("SOURCE", "BREAKING").put("BINARY", "NON_BREAKING")
                        .put("SEMANTIC", "POTENTIALLY_BREAKING").put("OTHER", "EQUIVALENT"))).set(
                                "differences",
                                JsonNodeFactory.instance.arrayNode()
                                        .add(JsonNodeFactory.instance.objectNode().put("code", "c1"))
                                        .add(JsonNodeFactory.instance.objectNode().put("code", "c2")))));

        Difference transformed = Util.transformAndAssumeOne(tr, oldEl, newEl,
                Difference.builder().withCode("c1").build());
        assertEquals(BREAKING, transformed.classification.get(SOURCE));
        assertEquals(NON_BREAKING, transformed.classification.get(BINARY));
        assertEquals(POTENTIALLY_BREAKING, transformed.classification.get(SEMANTIC));
        assertEquals(EQUIVALENT, transformed.classification.get(OTHER));

        transformed = Util.transformAndAssumeOne(tr, oldEl, newEl, Difference.builder().withCode("c2").build());
        assertNotNull(transformed);
        assertEquals(BREAKING, transformed.classification.get(SOURCE));
        assertEquals(NON_BREAKING, transformed.classification.get(BINARY));
        assertEquals(POTENTIALLY_BREAKING, transformed.classification.get(SEMANTIC));
        assertEquals(EQUIVALENT, transformed.classification.get(OTHER));

        transformed = Util.transformAndAssumeOne(tr, oldEl, newEl, Difference.builder().withCode("c3").build());
        assertNotNull(transformed);
        assertTrue(transformed.classification.isEmpty());
    }

    @Test
    public void testBulkIgnores() throws Exception {
        DifferencesTransform<DummyElement> tr = new DifferencesTransform<>();
        tr.initialize(context(JsonNodeFactory.instance.objectNode().put("ignore", true).set("differences",
                JsonNodeFactory.instance.arrayNode().add(JsonNodeFactory.instance.objectNode().put("code", "c1"))
                        .add(JsonNodeFactory.instance.objectNode().put("code", "c2")))));

        Difference transformed = Util.transformAndAssumeOne(tr, oldEl, newEl,
                Difference.builder().withCode("c1").build());
        assertNull(transformed);

        transformed = Util.transformAndAssumeOne(tr, oldEl, newEl, Difference.builder().withCode("c2").build());
        assertNull(transformed);

        transformed = Util.transformAndAssumeOne(tr, oldEl, newEl, Difference.builder().withCode("c3").build());
        assertNotNull(transformed);
    }

    @Test
    public void testBulkAddsAttachments() throws Exception {
        DifferencesTransform<DummyElement> tr = new DifferencesTransform<>();
        tr.initialize(context(((ObjectNode) JsonNodeFactory.instance.objectNode().set("attachments",
                JsonNodeFactory.instance.objectNode().put("attach1", "val1").put("attach2", "val2"))).set(
                        "differences",
                        JsonNodeFactory.instance.arrayNode()
                                .add(JsonNodeFactory.instance.objectNode().put("code", "c1"))
                                .add(JsonNodeFactory.instance.objectNode().put("code", "c2")))));

        Difference transformed = Util.transformAndAssumeOne(tr, oldEl, newEl,
                Difference.builder().withCode("c1").build());
        assertNotNull(transformed);
        assertEquals("val1", transformed.attachments.get("attach1"));
        assertEquals("val2", transformed.attachments.get("attach2"));

        transformed = Util.transformAndAssumeOne(tr, oldEl, newEl, Difference.builder().withCode("c2").build());
        assertNotNull(transformed);
        assertEquals("val1", transformed.attachments.get("attach1"));
        assertEquals("val2", transformed.attachments.get("attach2"));

        transformed = Util.transformAndAssumeOne(tr, oldEl, newEl, Difference.builder().withCode("c3").build());
        assertNotNull(transformed);
        assertTrue(transformed.attachments.isEmpty());
    }

    @Test
    public void testBulkMatcher() throws Exception {
        boolean[] matcherCalled = new boolean[1];

        TreeFilter<DummyElement> filter = mock(TreeFilter.class);
        when(filter.start(any())).thenAnswer(inv -> {
            DummyElement e = inv.getArgument(0);
            if (e != null && "element".equals(e.getFullHumanReadableString())) {
                matcherCalled[0] = true;
            }
            return FilterStartResult.doesntMatch();
        });

        when(filter.finish(any())).thenReturn(FilterFinishResult.direct(Ternary.FALSE));
        when(filter.finish()).thenReturn(emptyMap());

        ElementMatcher.CompiledRecipe recipe = mock(ElementMatcher.CompiledRecipe.class);
        when(recipe.filterFor(any())).thenReturn((TreeFilter) filter);

        ElementMatcher matcher = mock(ElementMatcher.class);
        when(matcher.getExtensionId()).thenReturn("testMatcher");
        when(matcher.compile(eq("element"))).thenReturn(Optional.of(recipe));

        when(oldEl.getFullHumanReadableString()).thenReturn("element");

        DifferencesTransform<DummyElement> tr = new DifferencesTransform<>();
        tr.initialize(context(JsonNodeFactory.instance.objectNode().put("matcher", "testMatcher").set("differences",
                JsonNodeFactory.instance.arrayNode()
                        .add(JsonNodeFactory.instance.objectNode().put("code", "code").put("old", "element"))))
                                .copyWithMatchers(singleton(matcher)));

        Util.transformAndAssumeOne(tr, oldEl, newEl, Difference.builder().withCode("code").build());

        assertTrue(matcherCalled[0]);
    }

    @Test
    public void testMatchByOldParent() throws Exception {
        DifferencesTransform<DummyElement> tr = new DifferencesTransform<>();
        tr.initialize(context(JsonNodeFactory.instance.objectNode().set("differences",
                JsonNodeFactory.instance.arrayNode().add(JsonNodeFactory.instance.objectNode().put("code", "code")
                        .put("old", "parent").put("justification", "matched")))));

        DummyElement parent = mock(DummyElement.class);
        when(parent.getFullHumanReadableString()).thenReturn("parent");
        when(oldEl.getParent()).thenReturn(parent);

        tr.startTraversal((ApiAnalyzer<DummyElement>) null, null, null).ifPresent(r -> {
            r.startElements(parent, null);
            r.startElements(oldEl, newEl);
            r.endElements(oldEl, newEl);
            r.endElements(parent, null);
            r.endTraversal();
        });

        TransformationResult res = tr.tryTransform(oldEl, newEl, Difference.builder().withCode("code").build());
        assertEquals(TransformationResult.Resolution.REPLACE, res.getResolution());
        assertNotNull(res.getDifferences());
        assertEquals(1, res.getDifferences().size());
        assertEquals("matched", res.getDifferences().iterator().next().justification);
    }

    @Test
    public void testMatchByNewParent() throws Exception {
        DifferencesTransform<DummyElement> tr = new DifferencesTransform<>();
        tr.initialize(context(JsonNodeFactory.instance.objectNode().set("differences",
                JsonNodeFactory.instance.arrayNode().add(JsonNodeFactory.instance.objectNode().put("code", "code")
                        .put("new", "parent").put("justification", "matched")))));

        DummyElement parent = mock(DummyElement.class);
        when(parent.getFullHumanReadableString()).thenReturn("parent");
        when(newEl.getParent()).thenReturn(parent);

        tr.startTraversal((ApiAnalyzer<DummyElement>) null, null, null).ifPresent(r -> {
            r.startElements(null, parent);
            r.startElements(oldEl, newEl);
            r.endElements(oldEl, newEl);
            r.endElements(null, parent);
            r.endTraversal();
        });

        TransformationResult res = tr.tryTransform(oldEl, newEl, Difference.builder().withCode("code").build());
        assertEquals(TransformationResult.Resolution.REPLACE, res.getResolution());
        assertNotNull(res.getDifferences());
        assertEquals(1, res.getDifferences().size());
        assertEquals("matched", res.getDifferences().iterator().next().justification);
    }

    @Test
    public void testMatchByBothParents() throws Exception {
        DifferencesTransform<DummyElement> tr = new DifferencesTransform<>();
        tr.initialize(context(JsonNodeFactory.instance.objectNode().set("differences",
                JsonNodeFactory.instance.arrayNode().add(JsonNodeFactory.instance.objectNode().put("code", "code")
                        .put("old", "oldParent").put("new", "newParent").put("justification", "matched")))));

        DummyElement oldParent = mock(DummyElement.class);
        when(oldParent.getFullHumanReadableString()).thenReturn("oldParent");
        when(oldEl.getParent()).thenReturn(oldParent);

        DummyElement newParent = mock(DummyElement.class);
        when(newParent.getFullHumanReadableString()).thenReturn("newParent");
        when(newEl.getParent()).thenReturn(newParent);

        tr.startTraversal((ApiAnalyzer<DummyElement>) null, null, null).ifPresent(r -> {
            r.startElements(oldParent, newParent);
            r.startElements(oldEl, newEl);
            r.endElements(oldEl, newEl);
            r.endElements(oldParent, newParent);
            r.endTraversal();
        });

        TransformationResult res = tr.tryTransform(oldEl, newEl, Difference.builder().withCode("code").build());
        assertEquals(TransformationResult.Resolution.REPLACE, res.getResolution());
        assertNotNull(res.getDifferences());
        assertEquals(1, res.getDifferences().size());
        assertEquals("matched", res.getDifferences().iterator().next().justification);
    }

    @Test
    public void testMatchByOldParentWithBothParentsPresent() throws Exception {
        DifferencesTransform<DummyElement> tr = new DifferencesTransform<>();
        tr.initialize(context(JsonNodeFactory.instance.objectNode().set("differences",
                JsonNodeFactory.instance.arrayNode().add(JsonNodeFactory.instance.objectNode().put("code", "code")
                        .put("old", "oldParent").put("justification", "matched")))));

        DummyElement oldParent = mock(DummyElement.class);
        when(oldParent.getFullHumanReadableString()).thenReturn("oldParent");
        when(oldEl.getParent()).thenReturn(oldParent);

        DummyElement newParent = mock(DummyElement.class);
        when(newEl.getParent()).thenReturn(newParent);

        tr.startTraversal((ApiAnalyzer<DummyElement>) null, null, null).ifPresent(r -> {
            r.startElements(oldParent, newParent);
            r.startElements(oldEl, newEl);
            r.endElements(oldEl, newEl);
            r.endElements(oldParent, newParent);
            r.endTraversal();
        });

        TransformationResult res = tr.tryTransform(oldEl, newEl, Difference.builder().withCode("code").build());
        assertEquals(TransformationResult.Resolution.REPLACE, res.getResolution());
        assertNotNull(res.getDifferences());
        assertEquals(1, res.getDifferences().size());
        assertEquals("matched", res.getDifferences().iterator().next().justification);
    }

    @Test
    public void testMatchByNewParentWithBothParentsPresent() throws Exception {
        DifferencesTransform<DummyElement> tr = new DifferencesTransform<>();
        tr.initialize(context(JsonNodeFactory.instance.objectNode().set("differences",
                JsonNodeFactory.instance.arrayNode().add(JsonNodeFactory.instance.objectNode().put("code", "code")
                        .put("new", "newParent").put("justification", "matched")))));

        DummyElement oldParent = mock(DummyElement.class);
        when(oldEl.getParent()).thenReturn(oldParent);

        DummyElement newParent = mock(DummyElement.class);
        when(newParent.getFullHumanReadableString()).thenReturn("newParent");
        when(newEl.getParent()).thenReturn(newParent);

        tr.startTraversal((ApiAnalyzer<DummyElement>) null, null, null).ifPresent(r -> {
            r.startElements(oldParent, newParent);
            r.startElements(oldEl, newEl);
            r.endElements(oldEl, newEl);
            r.endElements(oldParent, newParent);
            r.endTraversal();
        });

        TransformationResult res = tr.tryTransform(oldEl, newEl, Difference.builder().withCode("code").build());
        assertEquals(TransformationResult.Resolution.REPLACE, res.getResolution());
        assertNotNull(res.getDifferences());
        assertEquals(1, res.getDifferences().size());
        assertEquals("matched", res.getDifferences().iterator().next().justification);
    }

    private static AnalysisContext context(JsonNode configuration) {
        return AnalysisContext.builder().withOldAPI(EMPTY_API).withNewAPI(EMPTY_API).build()
                .copyWithConfiguration(configuration);
    }

    private static class DummyElement extends BaseElement<DummyElement> {
        public DummyElement() {
            super(null);
        }

        @Override
        public int compareTo(DummyElement o) {
            return 0;
        }
    }
}
