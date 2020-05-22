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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.9.0
 */
public class SchemaDrivenJSONToXmlConverterTest {

    @Test
    public void testBooleanConversion_true() throws Exception {
        ModelNode schema = json("{\"type\": \"boolean\"}");
        ModelNode json = json("true");

        PlexusConfiguration config = SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
        assertNotNull(config);
        assertEquals("tag", config.getName());
        assertEquals(0, config.getChildCount());
        assertEquals("true", config.getValue());
    }

    @Test
    public void testBooleanConversion_false() throws Exception {
        ModelNode schema = json("{\"type\": \"boolean\"}");
        ModelNode json = json("false");

        PlexusConfiguration config = SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
        assertNotNull(config);
        assertEquals("tag", config.getName());
        assertEquals(0, config.getChildCount());
        assertEquals("false", config.getValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBooleanConversion_invalid() throws Exception {
        ModelNode schema = json("{\"type\": \"boolean\"}");
        ModelNode json = json("kachny");

        SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
    }

    @Test
    public void testIntegerConversion_valid() throws Exception {
        ModelNode schema = json("{\"type\": \"integer\"}");
        ModelNode json = json("1");

        PlexusConfiguration config = SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
        assertNotNull(config);
        assertEquals("tag", config.getName());
        assertEquals(0, config.getChildCount());
        assertEquals("1", config.getValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIntegerConversion_invalid() throws Exception {
        ModelNode schema = json("{\"type\": \"integer\"}");
        ModelNode json = json("asdf");

        SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
    }

    @Test
    public void testNumberConversion_valid() throws Exception {
        ModelNode schema = json("{\"type\": \"number\"}");
        ModelNode json = json("1.2");

        PlexusConfiguration config = SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
        assertNotNull(config);
        assertEquals("tag", config.getName());
        assertEquals(0, config.getChildCount());
        assertEquals("1.2", config.getValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNumberConversion_invalid() throws Exception {
        ModelNode schema = json("{\"type\": \"number\"}");
        ModelNode json = json("1.2ff");

        SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
    }

    @Test
    public void testStringConversion() throws Exception {
        ModelNode schema = json("{\"type\": \"string\"}");
        ModelNode json = json("\"str\"");

        PlexusConfiguration config = SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
        assertNotNull(config);
        assertEquals("tag", config.getName());
        assertEquals(0, config.getChildCount());
        assertEquals("str", config.getValue());
    }

    @Test
    public void testStringConversion_escapes() throws Exception {
        ModelNode schema = json("{\"type\": \"string\"}");
        ModelNode json = json("\"&<>\"");

        PlexusConfiguration config = SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
        assertNotNull(config);
        assertEquals("tag", config.getName());
        assertEquals(0, config.getChildCount());
        assertEquals("&<>", config.getValue());
        StringWriter wrt = new StringWriter();
        XmlUtil.toIndentedString(config, 0, 0, wrt);
        assertEquals("<tag>&amp;&lt;&gt;</tag>", wrt.toString());
    }

    @Test
    public void testArrayConversion() throws Exception {
        ModelNode schema = json("{\"type\": \"array\", \"items\": {\"type\": \"integer\"}}");
        ModelNode json = json("[1,2,3]");

        PlexusConfiguration config = SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
        assertNotNull(config);
        assertEquals("tag", config.getName());
        assertNull(config.getValue());
        assertEquals(3, config.getChildCount());
        assertEquals("item", config.getChild(0).getName());
        assertEquals("1", config.getChild(0).getValue());
        assertEquals("item", config.getChild(1).getName());
        assertEquals("2", config.getChild(1).getValue());
        assertEquals("item", config.getChild(2).getName());
        assertEquals("3", config.getChild(2).getValue());
    }

    @Test
    public void testObjectConversion() throws Exception {
        ModelNode schema =
                json("{\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"integer\"}, " +
                        "\"b\": {\"type\": \"boolean\"}}}");
        ModelNode json = json("{\"a\": 1, \"b\": true}");

        PlexusConfiguration config = SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
        assertNotNull(config);
        assertNull(config.getValue());
        assertEquals(2, config.getChildCount());
        assertEquals("a", config.getChild(0).getName());
        assertEquals("1", config.getChild(0).getValue());
        assertEquals("b", config.getChild(1).getName());
        assertEquals("true", config.getChild(1).getValue());
    }

    @Test
    public void testObjectConversion_additionalProperties() throws Exception {
        ModelNode schema =
                json("{\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"integer\"}, " +
                        "\"b\": {\"type\": \"boolean\"}}, " +
                        "\"additionalProperties\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}}");
        ModelNode json = json("{\"a\": 4, \"b\": true, \"c\": [], \"d\": [\"x\"]}");

        PlexusConfiguration config = SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
        assertNotNull(config);
        assertNull(config.getValue());
        assertEquals(4, config.getChildCount());
        assertEquals("a", config.getChild(0).getName());
        assertEquals("4", config.getChild(0).getValue());
        assertEquals("b", config.getChild(1).getName());
        assertEquals("true", config.getChild(1).getValue());
        assertEquals("c", config.getChild(2).getName());
        assertNull(config.getChild(2).getValue());
        assertEquals(0, config.getChild(2).getChildCount());
        assertEquals("d", config.getChild(3).getName());
        assertNull(config.getChild(3).getValue());
        assertEquals(1, config.getChild(3).getChildCount());
        assertEquals("item", config.getChild(3).getChild(0).getName());
        assertEquals("x", config.getChild(3).getChild(0).getValue());
    }

    @Test
    public void testEnumBasedConversion() throws Exception {
        ModelNode schema = json("{\"enum\": [1, \"a\", true, 2.0]}");
        ModelNode json = json("1");

        PlexusConfiguration config = SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
        assertNotNull(config);
        assertEquals("tag", config.getName());
        assertEquals(0, config.getChildCount());
        assertEquals("1", config.getValue());

        json = json("\"a\"");
        config = SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
        assertNotNull(config);
        assertEquals("tag", config.getName());
        assertEquals(0, config.getChildCount());
        assertEquals("a", config.getValue());

        json = json("true");
        config = SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
        assertNotNull(config);
        assertEquals("tag", config.getName());
        assertEquals(0, config.getChildCount());
        assertEquals("true", config.getValue());

        json = json("2.0");
        config = SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
        assertNotNull(config);
        assertEquals("tag", config.getName());
        assertEquals(0, config.getChildCount());
        assertEquals("2.0", config.getValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEnumBasedConversion_unsupported() throws Exception {
        ModelNode schema = json("{\"enum\": [{}]}");
        ModelNode json = json("{}");

        SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
    }

    @Test
    public void testObjectConversion_with$refs() throws Exception {
        ModelNode schema = json("{\"type\": \"object\", \"properties\": {\"a\": {\"$ref\": \"#/definitions/a\"}}, " +
                "\"definitions\": {\"a\": {\"type\": \"boolean\"}}}");
        ModelNode json = json("{\"a\": true}");

        PlexusConfiguration config = SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", null);
        assertNotNull(config);
        assertEquals("tag", config.getName());
        assertNull(config.getValue());
        assertEquals(1, config.getChildCount());
        assertEquals("a", config.getChild(0).getName());
        assertEquals("true", config.getChild(0).getValue());
    }

    @Test
    public void testIdUsed() throws Exception {
        ModelNode schema = json("{\"type\": \"boolean\"}");
        ModelNode json = json("true");

        PlexusConfiguration config = SchemaDrivenJSONToXmlConverter.convert(json, schema, "tag", "id");
        assertNotNull(config);
        assertEquals("tag", config.getName());
        assertEquals("id", config.getAttribute("id"));
        assertEquals(0, config.getChildCount());
        assertEquals("true", config.getValue());
    }

    @Test
    public void testNestedSchemasConverted() throws Exception {
        ModelNode topSchema = json("{\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"string\"}}}");
        ModelNode nestedSchema = json("{\"type\": \"boolean\"}");

        Map<String, ModelNode> extensionSchemas = new HashMap<>();
        extensionSchemas.put("top", topSchema);
        extensionSchemas.put("top.nested", nestedSchema);

        ModelNode config = json("{\"top\": {\"a\": \"kachny\", \"nested\": true}}");

        PlexusConfiguration xml = SchemaDrivenJSONToXmlConverter.convertToXml(extensionSchemas, config);
        assertEquals(2, xml.getChildCount());

        PlexusConfiguration topXml = xml.getChild("top");
        assertNotNull(topXml);
        assertEquals("top", topXml.getName());
        assertEquals(1, topXml.getChildCount());
        assertEquals("a", topXml.getChild(0).getName());
        assertEquals("kachny", topXml.getChild("a").getValue());

        PlexusConfiguration nested = xml.getChild("top.nested");
        assertNotNull(nested);
        assertEquals(0, nested.getChildCount());
        assertEquals("true", nested.getValue());
    }


    @Test
    public void testOneOf() throws Exception {
        ModelNode schema = json("{\"oneOf\": [{\"type\": \"integer\"}, {\"type\": \"number\"}, {\"type\": \"boolean\"}]}");
        ModelNode json1 = json("1");
        ModelNode json2 = json("1.1");

        try {
            SchemaDrivenJSONToXmlConverter.convert(json1, schema, "ext", null);
            fail("Should not have been possible to convert using ambiguous oneOf.");
        } catch (IllegalArgumentException __) {
            //good
        }

        PlexusConfiguration xml = SchemaDrivenJSONToXmlConverter.convert(json2, schema, "ext", null);
        assertNotNull(xml);
        assertEquals("ext", xml.getName());
        assertEquals(0, xml.getChildCount());
        assertEquals("1.1", xml.getValue());
    }

    @Test
    public void testAnyOf() throws Exception {
        ModelNode schema = json("{\"anyOf\": [{\"type\": \"integer\"}, {\"type\": \"number\"}, {\"type\": \"boolean\"}]}");
        ModelNode json1 = json("1.1");
        ModelNode json2 = json("true");
        ModelNode json3 = json("\"asdf\"");

        PlexusConfiguration c1 = SchemaDrivenJSONToXmlConverter.convert(json1, schema, "ext", null);
        PlexusConfiguration c2 = SchemaDrivenJSONToXmlConverter.convert(json2, schema, "ext", null);

        try {
            SchemaDrivenJSONToXmlConverter.convert(json3, schema, "ext", null);
            fail("Invalid configuration should not have been converted.");
        } catch (IllegalArgumentException __) {
            //good
        }

        assertNotNull(c1);
        assertNotNull(c2);

        assertEquals("ext", c1.getName());
        assertEquals("ext", c2.getName());

        assertEquals(0, c1.getChildCount());
        assertEquals(0, c2.getChildCount());

        assertEquals("1.1", c1.getValue());
        assertEquals("true", c2.getValue());
    }

    @Test
    public void testAllOf() throws Exception {
        ModelNode schema = json("{\"allOf\": [{\"type\": \"integer\"}, {\"type\": \"number\"}]}");
        ModelNode json = json("1");

        PlexusConfiguration c = SchemaDrivenJSONToXmlConverter.convert(json, schema, "ext", null);

        assertNotNull(c);
        assertEquals("ext", c.getName());
        assertEquals(0, c.getChildCount());
        assertEquals("1", c.getValue());
    }


    private static ModelNode json(String json) {
        return ModelNode.fromJSONString(json);
    }
}
