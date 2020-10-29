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
package org.revapi.maven;

import static java.util.Collections.singletonList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import org.codehaus.plexus.interpolation.MapBasedValueSource;
import org.junit.Before;
import org.junit.Test;

public class PropertyValueResolverTest {

    private PropertyValueResolver resolver;
    private Map<String, String> mapping;

    @Before
    public void setup() {
        mapping = new HashMap<>();
        resolver = new PropertyValueResolver(singletonList(new MapBasedValueSource(mapping)));
    }

    @Test
    public void testResolve() {
        String value = resolver.resolve("some expression");
        assertEquals("some expression", value);
    }

    @Test(expected = IllegalStateException.class)
    public void testUnresolvedReference() {
        String value = resolver.resolve("${no-such-system-property}");
        fail("Did not fail with ISE: "+value);
    }

    @Test(expected = IllegalStateException.class)
    public void testIncompleteReference() {
        mapping.put("test.property1", "test.property1.value");
        String value = resolver.resolve("${test.property1");
        fail("Did not fail with ISE: "+value);
    }

    @Test
    public void testEscapedIncompleteReference() {
        String value = resolver.resolve("$${test.property1");
        assertEquals("${test.property1", value);
    }

    @Test(expected = IllegalStateException.class)
    public void testIncompleteReferenceFollowingSuccessfulResolve() {
        mapping.put("test.property1", "test.property1.value");
        String value = resolver.resolve("${test.property1} ${test.property1");
        fail("Did not fail with ISE: "+value);
    }

    @Test
    public void testRef() {
        mapping.put("test.property1", "test.property1.value");
        String value = resolver.resolve("${test.property1}");
        assertEquals("test.property1.value", value);
    }

    @Test
    public void testSystemRefs() {
        mapping.put("test.property2", "test.property2.value");
        String value = resolver.resolve("${test.property1,test.property2}");
        assertEquals("test.property2.value", value);
    }

    @Test
    public void testRefDefault() {
        String value = resolver.resolve("${test.property2:test.property2.default.value}");
        assertEquals("test.property2.default.value", value);
    }

    @Test
    public void testNesting() {
        assertEquals("{blah}", resolver.resolve("${resolves.to.nothing:{blah}}"));
        assertEquals("{blah}", resolver.resolve("${resolves.to.nothing,also.resolves.to.nothing:{blah}}"));
        mapping.put("var", "val");
        assertEquals("val", resolver.resolve("${var:{blah}}"));
        assertEquals("{{fo{o}oo}}", resolver.resolve("${resolves.to.nothing:{{fo{o}oo}}}"));
        assertEquals("blah{{fo{o}oo}}blah", resolver.resolve("${resolves.to.nothing:blah{{fo{o}oo}}}blah"));
    }

    @Test
    public void testRecursiveExpression() {
        mapping.put("var", "${var:val}");
        String value =  resolver.resolve("${var:val}");
        assertEquals("val", value);
    }
}
