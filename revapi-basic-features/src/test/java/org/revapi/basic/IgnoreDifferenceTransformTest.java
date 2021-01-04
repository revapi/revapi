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

import static org.revapi.basic.Util.getAnalysisContextFromFullConfig;

import javax.annotation.Nonnull;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.base.BaseElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class IgnoreDifferenceTransformTest {

    private static class DummyElement extends BaseElement<DummyElement> {

        private final String name;

        public DummyElement(String name) {
            super(null, null);
            this.name = name;
        }

        @Nonnull
        @Override
        public String getFullHumanReadableString() {
            return name;
        }

        @Override
        public int compareTo(@Nonnull DummyElement o) {
            return name.compareTo(o.name);
        }
    }

    @Test
    public void testSimpleTextMatch() throws Exception {
        DummyElement oldE = new DummyElement("a");
        DummyElement newE = new DummyElement("b");

        Difference difference = Difference.builder().withCode("c").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {

            AnalysisContext config = getAnalysisContextFromFullConfig(IgnoreDifferenceTransform.class,
                    "[{\"extension\": \"revapi.ignore\", \"configuration\": [{\"code\":\"c\", \"justification\" : \"because\"}]}]");

            t.initialize(config);
            difference = Util.transformAndAssumeOne(t, oldE, newE, difference);
            Assert.assertNull(difference);
        }
    }

    @Test
    public void testRegexMatch() throws Exception {
        DummyElement oldE = new DummyElement("a");
        DummyElement newE = new DummyElement("b");

        Difference difference = Difference.builder().withCode("c").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {
            AnalysisContext config = getAnalysisContextFromFullConfig(IgnoreDifferenceTransform.class,
                    "[{\"extension\": \"revapi.ignore\", \"configuration\": [{\"regex\": true, \"code\":\"c\", \"justification\" : \"because\"}]}]");

            t.initialize(config);
            difference = Util.transformAndAssumeOne(t, oldE, newE, difference);
            Assert.assertNull(difference);
        }
    }

    @Test
    public void testAttachmentMatch() throws Exception {
        DummyElement oldE = new DummyElement("a");
        DummyElement newE = new DummyElement("b");

        Difference difference = Difference.builder().withCode("c").addAttachment("kachna", "nedobra").build();
        Difference anotherDiff = Difference.builder().withCode("d").build();
        Difference matchingDiff = Difference.builder().withCode("c").addAttachment("kachna", "dobra").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {

            AnalysisContext config = getAnalysisContextFromFullConfig(IgnoreDifferenceTransform.class,
                    "[{\"extension\": \"revapi.ignore\", \"configuration\": [{\"code\":\"c\", \"kachna\": \"dobra\", \"justification\" : \"because\"}]}]");

            t.initialize(config);
            difference = Util.transformAndAssumeOne(t, oldE, newE, difference);
            Assert.assertNotNull(difference);

            anotherDiff = Util.transformAndAssumeOne(t, oldE, newE, anotherDiff);
            Assert.assertNotNull(anotherDiff);

            matchingDiff = Util.transformAndAssumeOne(t, oldE, newE, matchingDiff);
            Assert.assertNull(matchingDiff);
        }
    }

    @Test
    public void testAttachmentRegexMatch() throws Exception {
        DummyElement oldE = new DummyElement("a");
        DummyElement newE = new DummyElement("b");

        Difference difference = Difference.builder().withCode("c").addAttachment("kachna", "nedobra").build();
        Difference anotherDiff = Difference.builder().withCode("d").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {

            AnalysisContext config = getAnalysisContextFromFullConfig(IgnoreDifferenceTransform.class,
                    "[{\"extension\": \"revapi.ignore\", \"configuration\": [{\"regex\": true, \"code\":\".*\", \"kachna\": \".*dobra$\", \"justification\" : \"because\"}]}]");

            t.initialize(config);
            difference = Util.transformAndAssumeOne(t, oldE, newE, difference);
            Assert.assertNull(difference);

            anotherDiff = Util.transformAndAssumeOne(t, oldE, newE, anotherDiff);
            Assert.assertNotNull(anotherDiff);
        }
    }

    @Test
    public void testSimpleMatchHandlesNullOldElement() throws Exception {
        DummyElement newE = new DummyElement("b");

        Difference difference = Difference.builder().withCode("c").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {

            AnalysisContext config = getAnalysisContextFromFullConfig(IgnoreDifferenceTransform.class,
                    "[{\"extension\": \"revapi.ignore\", \"configuration\": [{\"code\": \"c\", \"old\":\"a\", \"justification\" : \"because\"}]}]");

            t.initialize(config);
            Difference transformed = Util.transformAndAssumeOne(t, null, newE, difference);
            Assert.assertSame(difference, transformed);
        }
    }

    @Test
    public void testSimpleMatchHandlesNullNewElement() throws Exception {
        DummyElement oldE = new DummyElement("a");

        Difference difference = Difference.builder().withCode("c").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {

            AnalysisContext config = getAnalysisContextFromFullConfig(IgnoreDifferenceTransform.class,
                    "[{\"extension\": \"revapi.ignore\", \"configuration\": [{\"code\": \"c\", \"new\":\"b\", \"justification\" : \"because\"}]}]");

            t.initialize(config);
            Difference transformed = Util.transformAndAssumeOne(t, oldE, null, difference);
            Assert.assertSame(difference, transformed);
        }
    }

    @Test
    public void testRegexMatchHandlesNullOldElement() throws Exception {
        DummyElement newE = new DummyElement("b");

        Difference difference = Difference.builder().withCode("c").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {

            AnalysisContext config = getAnalysisContextFromFullConfig(IgnoreDifferenceTransform.class,
                    "[{\"extension\": \"revapi.ignore\", \"configuration\": [{\"regex\": true, \"code\": \"c\", \"old\":\"a\", \"justification\" : \"because\"}]}]");

            t.initialize(config);
            Difference transformed = Util.transformAndAssumeOne(t, null, newE, difference);
            Assert.assertSame(difference, transformed);
        }
    }

    @Test
    public void testRegexMatchHandlesNullNewElement() throws Exception {
        DummyElement oldE = new DummyElement("a");

        Difference difference = Difference.builder().withCode("c").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {

            AnalysisContext config = getAnalysisContextFromFullConfig(IgnoreDifferenceTransform.class,
                    "[{\"extension\": \"revapi.ignore\", \"configuration\": [{\"regex\": true, \"code\": \"c\", \"new\":\"b\", \"justification\" : \"because\"}]}]");

            t.initialize(config);
            Difference transformed = Util.transformAndAssumeOne(t, oldE, null, difference);
            Assert.assertSame(difference, transformed);
        }
    }

    @Test
    public void testSimpleMatchOnOldElement() throws Exception {
        DummyElement oldE = new DummyElement("a");
        DummyElement newE = new DummyElement("b");

        Difference difference = Difference.builder().withCode("c").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {

            AnalysisContext config = getAnalysisContextFromFullConfig(IgnoreDifferenceTransform.class,
                    "[{\"extension\": \"revapi.ignore\", \"configuration\": [{\"code\":\"c\", \"old\": \"a\", \"justification\" : \"because\"}]}]");

            t.initialize(config);
            difference = Util.transformAndAssumeOne(t, oldE, newE, difference);
            Assert.assertNull(difference);
        }

        difference = Difference.builder().withCode("c").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {

            AnalysisContext config = getAnalysisContextFromFullConfig(IgnoreDifferenceTransform.class,
                    "[{\"extension\": \"revapi.ignore\", \"configuration\": [{\"code\":\"c\", \"old\": \"x\", \"justification\" : \"because\"}]}]");

            t.initialize(config);
            Difference transformed = Util.transformAndAssumeOne(t, oldE, newE, difference);
            Assert.assertSame(difference, transformed);
        }
    }

    @Test
    public void testSimpleMatchOnNewElement() throws Exception {
        DummyElement oldE = new DummyElement("a");
        DummyElement newE = new DummyElement("b");

        Difference difference = Difference.builder().withCode("c").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {

            AnalysisContext config = getAnalysisContextFromFullConfig(IgnoreDifferenceTransform.class,
                    "[{\"extension\": \"revapi.ignore\", \"configuration\": [{\"code\":\"c\", \"new\": \"b\", \"justification\" : \"because\"}]}]");

            t.initialize(config);
            difference = Util.transformAndAssumeOne(t, oldE, newE, difference);
            Assert.assertNull(difference);
        }

        difference = Difference.builder().withCode("c").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {

            AnalysisContext config = getAnalysisContextFromFullConfig(IgnoreDifferenceTransform.class,
                    "[{\"extension\": \"revapi.ignore\", \"configuration\": [{\"code\":\"c\", \"new\": \"x\", \"justification\" : \"because\"}]}]");

            t.initialize(config);
            Difference transformed = Util.transformAndAssumeOne(t, oldE, newE, difference);
            Assert.assertSame(difference, transformed);
        }
    }

    @Test
    public void testRegexMatchOnOldElement() throws Exception {
        DummyElement oldE = new DummyElement("a");
        DummyElement newE = new DummyElement("b");

        Difference difference = Difference.builder().withCode("c").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {

            AnalysisContext config = getAnalysisContextFromFullConfig(IgnoreDifferenceTransform.class,
                    "[{\"extension\": \"revapi.ignore\", \"configuration\": [{\"regex\": true, \"code\":\"c\", \"old\": \"[aA]\", \"justification\" : \"because\"}]}]");

            t.initialize(config);
            difference = Util.transformAndAssumeOne(t, oldE, newE, difference);
            Assert.assertNull(difference);
        }

        difference = Difference.builder().withCode("c").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {

            AnalysisContext config = getAnalysisContextFromFullConfig(IgnoreDifferenceTransform.class,
                    "[{\"extension\": \"revapi.ignore\", \"configuration\": [{\"regex\": true, \"code\":\"c\", \"old\": \"x\", \"justification\" : \"because\"}]}]");

            t.initialize(config);
            Difference transformed = Util.transformAndAssumeOne(t, oldE, newE, difference);
            Assert.assertSame(difference, transformed);
        }
    }

    @Test
    public void testRegexMatchOnNewElement() throws Exception {
        DummyElement oldE = new DummyElement("a");
        DummyElement newE = new DummyElement("b");

        Difference difference = Difference.builder().withCode("c").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {

            AnalysisContext config = getAnalysisContextFromFullConfig(IgnoreDifferenceTransform.class,
                    "[{\"extension\": \"revapi.ignore\", \"configuration\": [{\"regex\": true, \"code\":\"c\", \"new\": \"[bB]\", \"justification\" : \"because\"}]}]");

            t.initialize(config);
            difference = Util.transformAndAssumeOne(t, oldE, newE, difference);
            Assert.assertNull(difference);
        }

        difference = Difference.builder().withCode("c").build();

        try (IgnoreDifferenceTransform t = new IgnoreDifferenceTransform()) {

            AnalysisContext config = getAnalysisContextFromFullConfig(IgnoreDifferenceTransform.class,
                    "[{\"extension\": \"revapi.ignore\", \"configuration\": [{\"regex\": true, \"code\":\"c\", \"new\": \"x\", \"justification\" : \"because\"}]}]");

            t.initialize(config);
            Difference transformed = Util.transformAndAssumeOne(t, oldE, newE, difference);
            Assert.assertSame(difference, transformed);
        }
    }
}
