/*
 * Copyright 2017 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi.maven;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;

import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.9.0
 */
public class SchemaDrivenXmlToJSONConverterTest {

    @Test
    public void testBooleanConversion_true() throws Exception {
        ModelNode schema = json("{\"type\": \"boolean\"}");
        PlexusConfiguration xml = xml("<config>true</config>");

        ModelNode config = SchemaDrivenXmlToJSONConverter.convert(xml, schema);
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.BOOLEAN, config.getType());
        Assert.assertTrue(config.asBoolean());
    }

    @Test
    public void testBooleanConversion_false() throws Exception {
        ModelNode schema = json("{\"type\": \"boolean\"}");
        PlexusConfiguration xml = xml("<config>false</config>");

        ModelNode config = SchemaDrivenXmlToJSONConverter.convert(xml, schema);
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.BOOLEAN, config.getType());
        Assert.assertFalse(config.asBoolean());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBooleanConversion_invalid() throws Exception {
        ModelNode schema = json("{\"type\": \"boolean\"}");
        PlexusConfiguration xml = xml("<config>asdf</config>");

        SchemaDrivenXmlToJSONConverter.convert(xml, schema);
    }

    @Test
    public void testIntegerConversion_valid() throws Exception {
        ModelNode schema = json("{\"type\": \"integer\"}");
        PlexusConfiguration xml = xml("<config>1</config>");

        ModelNode config = SchemaDrivenXmlToJSONConverter.convert(xml, schema);
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.LONG, config.getType());
        Assert.assertEquals(1, config.asLong());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIntegerConversion_invalid() throws Exception {
        ModelNode schema = json("{\"type\": \"integer\"}");
        PlexusConfiguration xml = xml("<config>some</config>");

        SchemaDrivenXmlToJSONConverter.convert(xml, schema);
    }

    @Test
    public void testNumberConversion_valid() throws Exception {
        ModelNode schema = json("{\"type\": \"number\"}");
        PlexusConfiguration xml = xml("<config>1.2</config>");

        ModelNode config = SchemaDrivenXmlToJSONConverter.convert(xml, schema);
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.DOUBLE, config.getType());
        Assert.assertEquals(1.2d, config.asDouble(), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNumberConversion_invalid() throws Exception {
        ModelNode schema = json("{\"type\": \"number\"}");
        PlexusConfiguration xml = xml("<config>some</config>");

        SchemaDrivenXmlToJSONConverter.convert(xml, schema);
    }

    @Test
    public void testStringConversion() throws Exception {
        ModelNode schema = json("{\"type\": \"string\"}");
        PlexusConfiguration xml = xml("<config>1.2</config>");

        ModelNode config = SchemaDrivenXmlToJSONConverter.convert(xml, schema);
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.STRING, config.getType());
        Assert.assertEquals("1.2", config.asString());
    }

    @Test
    public void testArrayConversion() throws Exception {
        ModelNode schema = json("{\"type\": \"array\", \"items\": {\"type\": \"integer\"}}");
        PlexusConfiguration xml = xml("<list><item>1</item><item>2</item></list>");

        ModelNode config = SchemaDrivenXmlToJSONConverter.convert(xml, schema);
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.LIST, config.getType());
        Assert.assertEquals(2, config.asList().size());
        Assert.assertEquals(1L, config.asList().get(0).asLong());
        Assert.assertEquals(2L, config.asList().get(1).asLong());
    }

    @Test
    public void testObjectConversion() throws Exception {
        ModelNode schema =
                json("{\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"integer\"}, " +
                        "\"b\": {\"type\": \"boolean\"}}}");
        PlexusConfiguration xml = xml("<config><a>4</a><b>true</b></config>");

        ModelNode config = SchemaDrivenXmlToJSONConverter.convert(xml, schema);
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.OBJECT, config.getType());
        Assert.assertEquals(4L, config.get("a").asLong());
        Assert.assertEquals(true, config.get("b").asBoolean());
    }

    @Test
    public void testObjectConversion_additionalProperties() throws Exception {
        ModelNode schema =
                json("{\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"integer\"}, " +
                        "\"b\": {\"type\": \"boolean\"}}, " +
                        "\"additionalProperties\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}}");
        PlexusConfiguration xml = xml("<config><a>4</a><b>true</b><c/><d><x>x</x></d></config>");

        ModelNode config = SchemaDrivenXmlToJSONConverter.convert(xml, schema);
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.OBJECT, config.getType());
        Assert.assertEquals(4L, config.get("a").asLong());
        Assert.assertEquals(true, config.get("b").asBoolean());
        Assert.assertEquals(Collections.emptyList(), config.get("c").asList());
        Assert.assertEquals(1, config.get("d").asList().size());
        Assert.assertEquals("x", config.get("d").get(0).asString());
    }

    @Test
    public void testEnumBasedConversion() throws Exception {
        ModelNode schema = json("{\"enum\": [1, \"a\", true, 2.0]}");
        PlexusConfiguration xml = xml("<config>1</config>");

        ModelNode config = SchemaDrivenXmlToJSONConverter.convert(xml, schema);
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.LONG, config.getType());
        Assert.assertEquals(1L, config.asLong());

        xml = xml("<config>a</config>");
        config = SchemaDrivenXmlToJSONConverter.convert(xml, schema);
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.STRING, config.getType());
        Assert.assertEquals("a", config.asString());

        xml = xml("<config>true</config>");
        config = SchemaDrivenXmlToJSONConverter.convert(xml, schema);
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.BOOLEAN, config.getType());
        Assert.assertEquals(true, config.asBoolean());

        xml = xml("<config>2.0</config>");
        config = SchemaDrivenXmlToJSONConverter.convert(xml, schema);
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.DOUBLE, config.getType());
        Assert.assertEquals(2.0d, config.asDouble(), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEnumBasedConversion_unsupported() throws Exception {
        ModelNode schema = json("{\"enum\": [{}]}");
        PlexusConfiguration xml = xml("<config>a</config>");

        SchemaDrivenXmlToJSONConverter.convert(xml, schema);
    }

    @Test
    public void testObjectConversion_with$refs() throws Exception {
        ModelNode schema = json("{\"type\": \"object\", \"properties\": {\"a\": {\"$ref\": \"#/definitions/a\"}}, " +
                "\"definitions\": {\"a\": {\"type\": \"boolean\"}}}");
        PlexusConfiguration xml = xml("<config><a>true</a></config>");

        ModelNode config = SchemaDrivenXmlToJSONConverter.convert(xml, schema);
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.OBJECT, config.getType());
        Assert.assertEquals(true, config.get("a").asBoolean());
    }

    private static PlexusConfiguration xml(String xml) throws IOException, XmlPullParserException {
        Xpp3Dom dom = Xpp3DomBuilder.build(new StringReader(xml));
        return new XmlPlexusConfiguration(dom);
    }

    private static ModelNode json(String json) {
        return ModelNode.fromJSONString(json);
    }
}
