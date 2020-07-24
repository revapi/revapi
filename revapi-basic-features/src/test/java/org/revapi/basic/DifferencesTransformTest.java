/*
 * Copyright 2014-2020 Lukas Krejci
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.revapi.CompatibilityType.BINARY;
import static org.revapi.CompatibilityType.OTHER;
import static org.revapi.CompatibilityType.SEMANTIC;
import static org.revapi.CompatibilityType.SOURCE;
import static org.revapi.DifferenceSeverity.BREAKING;
import static org.revapi.DifferenceSeverity.EQUIVALENT;
import static org.revapi.DifferenceSeverity.NON_BREAKING;
import static org.revapi.DifferenceSeverity.POTENTIALLY_BREAKING;

import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Criticality;
import org.revapi.Difference;
import org.revapi.Element;

@RunWith(MockitoJUnitRunner.class)
public class DifferencesTransformTest {

    @Mock
    private Element oldEl;

    @Mock
    private Element newEl;

    private static final API EMPTY_API = API.builder().build();

    @Test
    public void testAddsJustification() throws Exception {
        DifferencesTransform tr = new DifferencesTransform();
        tr.initialize(context(new ModelNode()
                .add("differences", new ModelNode()
                        .add(new ModelNode()
                                .add("regex", true)
                                .add("code", ".*")
                                .add("justification", "because")
                                .asObject())
                )
                .asObject()));

        Difference transformed = tr.transform(oldEl, newEl, Difference.builder().withCode("whatevs").build());

        assertNotNull(transformed);
        assertEquals("because", transformed.justification);
    }

    @Test
    public void testAddsCriticality() throws Exception {
        DifferencesTransform tr = new DifferencesTransform();
        tr.initialize(context(new ModelNode()
                .add("differences", new ModelNode()
                        .add(new ModelNode()
                                .add("regex", true)
                                .add("code", ".*")
                                .add("criticality", "documented")
                                .asObject())
                )
                .asObject()));

        Difference transformed = tr.transform(oldEl, newEl, Difference.builder().withCode("whatevs").build());

        assertNotNull(transformed);
        assertEquals(Criticality.DOCUMENTED, transformed.criticality);
    }

    @Test
    public void testAddsClassification() throws Exception {
        DifferencesTransform tr = new DifferencesTransform();
        tr.initialize(context(new ModelNode()
                .add("differences", new ModelNode()
                        .add(new ModelNode()
                                .add("regex", true)
                                .add("code", ".*")
                                .add("classify", new ModelNode()
                                        .add("SOURCE", "BREAKING")
                                        .add("BINARY", "NON_BREAKING")
                                        .add("SEMANTIC", "POTENTIALLY_BREAKING")
                                        .add("OTHER", "EQUIVALENT")
                                        .asObject())
                                .asObject())
                )
                .asObject()));

        Difference transformed = tr.transform(oldEl, newEl, Difference.builder()
                .withCode("whatevs")
                .build());

        assertNotNull(transformed);
        assertEquals(BREAKING, transformed.classification.get(SOURCE));
        assertEquals(NON_BREAKING, transformed.classification.get(BINARY));
        assertEquals(POTENTIALLY_BREAKING, transformed.classification.get(SEMANTIC));
        assertEquals(EQUIVALENT, transformed.classification.get(OTHER));

        transformed = tr.transform(oldEl, newEl, Difference.builder()
                .withCode("whatevs")
                .addClassification(SOURCE, BREAKING)
                .addClassification(BINARY, BREAKING)
                .addClassification(SEMANTIC, BREAKING)
                .addClassification(OTHER, BREAKING)
                .build());

        assertNotNull(transformed);
        assertEquals(BREAKING, transformed.classification.get(SOURCE));
        assertEquals(NON_BREAKING, transformed.classification.get(BINARY));
        assertEquals(POTENTIALLY_BREAKING, transformed.classification.get(SEMANTIC));
        assertEquals(EQUIVALENT, transformed.classification.get(OTHER));
    }

    @Test
    public void testIgnores() throws Exception {
        DifferencesTransform tr = new DifferencesTransform();
        tr.initialize(context(new ModelNode()
                .add("differences", new ModelNode()
                        .add(new ModelNode()
                                .add("regex", true)
                                .add("code", ".*")
                                .add("ignore", true)
                                .asObject())
                )
                .asObject()));

        Difference transformed = tr.transform(oldEl, newEl, Difference.builder()
                .withCode("whatevs")
                .build());

        assertNull(transformed);
    }

    @Test
    public void testAddsAttachments() throws Exception {
        DifferencesTransform tr = new DifferencesTransform();
        tr.initialize(context(new ModelNode()
                .add("differences", new ModelNode()
                        .add(new ModelNode()
                                .add("regex", true)
                                .add("code", ".*")
                                .add("attachments", new ModelNode()
                                        .add("attach1", "val1")
                                        .add("attach2", "val2")
                                        .asObject())
                                .asObject())
                )
                .asObject()));

        Difference transformed = tr.transform(oldEl, newEl, Difference.builder()
                .withCode("whatevs")
                .addAttachment("attach1", "originalValue")
                .build());

        assertNotNull(transformed);
        assertEquals("val1", transformed.attachments.get("attach1"));
        assertEquals("val2", transformed.attachments.get("attach2"));
    }

    @Test
    public void testBulkAddsJustification() throws Exception {
        DifferencesTransform tr = new DifferencesTransform();
        tr.initialize(context(new ModelNode()
                .add("justification", "all of this is cool")
                .add("differences", new ModelNode()
                        .add(new ModelNode()
                                .add("code", "c1")
                                .asObject())
                        .add(new ModelNode()
                                .add("code", "c2")
                                .asObject())
                )
                .asObject()));

        Difference transformed = tr.transform(oldEl, newEl, Difference.builder().withCode("c1").build());
        assertNotNull(transformed);
        assertEquals("all of this is cool", transformed.justification);

        transformed = tr.transform(oldEl, newEl, Difference.builder().withCode("c2").build());
        assertNotNull(transformed);
        assertEquals("all of this is cool", transformed.justification);

        transformed = tr.transform(oldEl, newEl, Difference.builder().withCode("c3").build());
        assertNotNull(transformed);
        assertNull(transformed.justification);
    }

    @Test
    public void testBulkAddsCriticality() throws Exception {
        DifferencesTransform tr = new DifferencesTransform();
        tr.initialize(context(new ModelNode()
                .add("criticality", "highlight")
                .add("differences", new ModelNode()
                        .add(new ModelNode()
                                .add("code", "c1")
                                .asObject())
                        .add(new ModelNode()
                                .add("code", "c2")
                                .asObject())
                )
                .asObject()));

        Difference transformed = tr.transform(oldEl, newEl, Difference.builder().withCode("c1").build());
        assertNotNull(transformed);
        assertEquals(Criticality.HIGHLIGHT, transformed.criticality);

        transformed = tr.transform(oldEl, newEl, Difference.builder().withCode("c2").build());
        assertNotNull(transformed);
        assertEquals(Criticality.HIGHLIGHT, transformed.criticality);

        transformed = tr.transform(oldEl, newEl, Difference.builder().withCode("c3").build());
        assertNotNull(transformed);
        assertNull(transformed.criticality);
    }

    @Test
    public void testBulkAddsClassification() throws Exception {
        DifferencesTransform tr = new DifferencesTransform();
        tr.initialize(context(new ModelNode()
                .add("classify", new ModelNode()
                        .add("SOURCE", "BREAKING")
                        .add("BINARY", "NON_BREAKING")
                        .add("SEMANTIC", "POTENTIALLY_BREAKING")
                        .add("OTHER", "EQUIVALENT")
                        .asObject())
                .add("differences", new ModelNode()
                        .add(new ModelNode()
                                .add("code", "c1")
                                .asObject())
                        .add(new ModelNode()
                                .add("code", "c2")
                                .asObject())
                )
                .asObject()));

        Difference transformed = tr.transform(oldEl, newEl, Difference.builder().withCode("c1").build());
        assertNotNull(transformed);
        assertEquals(BREAKING, transformed.classification.get(SOURCE));
        assertEquals(NON_BREAKING, transformed.classification.get(BINARY));
        assertEquals(POTENTIALLY_BREAKING, transformed.classification.get(SEMANTIC));
        assertEquals(EQUIVALENT, transformed.classification.get(OTHER));

        transformed = tr.transform(oldEl, newEl, Difference.builder().withCode("c2").build());
        assertNotNull(transformed);
        assertEquals(BREAKING, transformed.classification.get(SOURCE));
        assertEquals(NON_BREAKING, transformed.classification.get(BINARY));
        assertEquals(POTENTIALLY_BREAKING, transformed.classification.get(SEMANTIC));
        assertEquals(EQUIVALENT, transformed.classification.get(OTHER));

        transformed = tr.transform(oldEl, newEl, Difference.builder().withCode("c3").build());
        assertNotNull(transformed);
        assertTrue(transformed.classification.isEmpty());
    }

    @Test
    public void testBulkIgnores() throws Exception {
        DifferencesTransform tr = new DifferencesTransform();
        tr.initialize(context(new ModelNode()
                .add("ignore", true)
                .add("differences", new ModelNode()
                        .add(new ModelNode()
                                .add("code", "c1")
                                .asObject())
                        .add(new ModelNode()
                                .add("code", "c2")
                                .asObject())
                )
                .asObject()));

        Difference transformed = tr.transform(oldEl, newEl, Difference.builder().withCode("c1").build());
        assertNull(transformed);

        transformed = tr.transform(oldEl, newEl, Difference.builder().withCode("c2").build());
        assertNull(transformed);

        transformed = tr.transform(oldEl, newEl, Difference.builder().withCode("c3").build());
        assertNotNull(transformed);
    }

    @Test
    public void testBulkAddsAttachments() throws Exception {
        DifferencesTransform tr = new DifferencesTransform();
        tr.initialize(context(new ModelNode()
                .add("attachments", new ModelNode()
                        .add("attach1", "val1")
                        .add("attach2", "val2")
                        .asObject())
                .add("differences", new ModelNode()
                        .add(new ModelNode()
                                .add("code", "c1")
                                .asObject())
                        .add(new ModelNode()
                                .add("code", "c2")
                                .asObject())
                )
                .asObject()));

        Difference transformed = tr.transform(oldEl, newEl, Difference.builder().withCode("c1").build());
        assertNotNull(transformed);
        assertEquals("val1", transformed.attachments.get("attach1"));
        assertEquals("val2", transformed.attachments.get("attach2"));

        transformed = tr.transform(oldEl, newEl, Difference.builder().withCode("c2").build());
        assertNotNull(transformed);
        assertEquals("val1", transformed.attachments.get("attach1"));
        assertEquals("val2", transformed.attachments.get("attach2"));

        transformed = tr.transform(oldEl, newEl, Difference.builder().withCode("c3").build());
        assertNotNull(transformed);
        assertTrue(transformed.attachments.isEmpty());
    }

    private static AnalysisContext context(ModelNode configuration) {
        return AnalysisContext.builder()
                .withOldAPI(EMPTY_API)
                .withNewAPI(EMPTY_API)
                .build()
                .copyWithConfiguration(configuration);
    }
}
