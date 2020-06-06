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
package org.revapi.configuration;

import java.io.IOException;
import java.io.StringReader;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author Lukas Krejci
 * @since 0.8.0
 */
public class XmlToJsonTest {

    @Test
    public void testBooleanConversion_true() throws Exception {
        XmlToJson<Node> converter = converter("ext", "{\"type\": \"boolean\"}");
        Node xml = xml("<config><ext id='1'>true</ext></config>");

        ModelNode extConf = converter.convert(xml).get(0);
        Assert.assertEquals("1", extConf.get("id").asString());
        ModelNode config = extConf.get("configuration");
        Assert.assertEquals(ModelType.BOOLEAN, config.getType());
        Assert.assertTrue(config.asBoolean());
    }

    @Test
    public void testBooleanConversion_false() throws Exception {
        XmlToJson<Node> converter = converter("ext", "{\"type\": \"boolean\"}");
        Node xml = xml("<config><ext>false</ext></config>");

        ModelNode config = converter.convert(xml).get(0).get("configuration");
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.BOOLEAN, config.getType());
        Assert.assertFalse(config.asBoolean());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBooleanConversion_invalid() throws Exception {
        XmlToJson<Node> converter = converter("ext", "{\"type\": \"boolean\"}");
        Node xml = xml("<config><ext>asdf</ext></config>");

        converter.convert(xml);
    }

    @Test
    public void testIntegerConversion_valid() throws Exception {
        XmlToJson<Node> converter = converter("ext", "{\"type\": \"integer\"}");
        Node xml = xml("<config><ext>1</ext></config>");

        ModelNode config = converter.convert(xml).get(0).get("configuration");
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.LONG, config.getType());
        Assert.assertEquals(1, config.asLong());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIntegerConversion_invalid() throws Exception {
        XmlToJson<Node> converter = converter("ext", "{\"type\": \"integer\"}");
        Node xml = xml("<config><ext>some</ext></config>");

        converter.convert(xml);
    }

    @Test
    public void testNumberConversion_valid() throws Exception {
        XmlToJson<Node> converter = converter("ext", "{\"type\": \"number\"}");
        Node xml = xml("<config><ext>1.2</ext></config>");

        ModelNode config = converter.convert(xml).get(0).get("configuration");
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.DOUBLE, config.getType());
        Assert.assertEquals(1.2d, config.asDouble(), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNumberConversion_invalid() throws Exception {
        XmlToJson<Node> converter = converter("ext", "{\"type\": \"number\"}");
        Node xml = xml("<config><ext>some</ext></config>");

        converter.convert(xml);
    }

    @Test
    public void testStringConversion() throws Exception {
        XmlToJson<Node> converter = converter("ext", "{\"type\": \"string\"}");
        Node xml = xml("<config><ext>1.2</ext></config>");

        ModelNode config = converter.convert(xml).get(0).get("configuration");
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.STRING, config.getType());
        Assert.assertEquals("1.2", config.asString());
    }

    @Test
    public void testArrayConversion() throws Exception {
        XmlToJson<Node> converter = converter("list", "{\"type\": \"array\", \"items\": {\"type\": \"integer\"}}");
        Node xml = xml("<config><list><item>1</item>\n\n  <item>2</item></list></config>");

        ModelNode config = converter.convert(xml).get(0).get("configuration");
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.LIST, config.getType());
        Assert.assertEquals(2, config.asList().size());
        Assert.assertEquals(1L, config.asList().get(0).asLong());
        Assert.assertEquals(2L, config.asList().get(1).asLong());
    }

    @Test
    public void testArrayConversion_commentsAndWhitespaceIgnored() throws Exception {
        XmlToJson<Node> converter = converter("list", "{\"type\": \"array\", \"items\": {\"type\": \"integer\"}}");
        Node xml = xml("<config><list>\n\n   <!-- just whitespace -->\n\t   </list></config>");

        ModelNode config = converter.convert(xml).get(0).get("configuration");
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.LIST, config.getType());
        Assert.assertTrue(config.asList().isEmpty());
    }

    @Test
    public void testArrayConversion_invalid() throws Exception {
        try {
            XmlToJson<Node> converter = converter("list", "{\"type\": \"array\", \"items\": {\"type\": \"integer\"}}");
            Node xml = xml("<config><list>text is invalid</list></config>");

            converter.convert(xml).get(0).get("configuration");

            Assert.fail("Invalid array conversion shouldn't have succeeded.");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("<list>"));
        }
    }

    @Test
    public void testObjectConversion() throws Exception {
        XmlToJson<Node> converter = converter("ext", "{\"type\": \"object\", " +
                "\"properties\": {\"a\": {\"type\": \"integer\"}, \"b\": {\"type\": \"boolean\"}}}");
        Node xml = xml("<config><ext><a>4</a><b>true</b></ext></config>");

        ModelNode config = converter.convert(xml).get(0).get("configuration");
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.OBJECT, config.getType());
        Assert.assertEquals(4L, config.get("a").asLong());
        Assert.assertEquals(true, config.get("b").asBoolean());
    }

    @Test
    public void testObjectConversion_additionalProperties() throws Exception {
        XmlToJson<Node> converter = converter("ext", "{\"type\": \"object\", " +
                "\"properties\": {\"a\": {\"type\": \"integer\"}, \"b\": {\"type\": \"boolean\"}}, " +
                        "\"additionalProperties\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}}");
        Node xml = xml("<config><ext><a>4</a><b>true</b><c/><d><x>x</x></d></ext></config>");

        ModelNode config = converter.convert(xml).get(0).get("configuration");
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
        XmlToJson<Node> converter = converter("ext", "{\"enum\": [1, \"a\", true, 2.0]}");
        Node xml = xml("<config><ext>1</ext></config>");

        ModelNode config = converter.convert(xml).get(0).get("configuration");
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.LONG, config.getType());
        Assert.assertEquals(1L, config.asLong());

        xml = xml("<config><ext>a</ext></config>");
        config = converter.convert(xml).get(0).get("configuration");
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.STRING, config.getType());
        Assert.assertEquals("a", config.asString());

        xml = xml("<config><ext>true</ext></config>");
        config = converter.convert(xml).get(0).get("configuration");
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.BOOLEAN, config.getType());
        Assert.assertEquals(true, config.asBoolean());

        xml = xml("<config><ext>2.0</ext></config>");
        config = converter.convert(xml).get(0).get("configuration");
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.DOUBLE, config.getType());
        Assert.assertEquals(2.0d, config.asDouble(), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEnumBasedConversion_unsupported() throws Exception {
        XmlToJson<Node> converter = converter("ext", "{\"enum\": [{}]}");
        Node xml = xml("<config><ext>a</ext></config>");

        converter.convert(xml);
    }

    @Test
    public void testObjectConversion_with$refs() throws Exception {
        XmlToJson<Node> converter = converter("ext", "{\"type\": \"object\", \"properties\": {\"a\": {\"$ref\": \"#/definitions/a\"}}, " +
                "\"definitions\": {\"a\": {\"type\": \"boolean\"}}}");
        Node xml = xml("<config><ext><a>true</a></ext></config>");

        ModelNode config = converter.convert(xml).get(0).get("configuration");
        Assert.assertNotNull(config);
        Assert.assertEquals(ModelType.OBJECT, config.getType());
        Assert.assertEquals(true, config.get("a").asBoolean());
    }

    @Test
    public void testOneOf() throws Exception {
        XmlToJson<Node> converter = converter("ext", "{\"oneOf\": [{\"type\": \"integer\"}, {\"type\": \"number\"}, {\"type\": \"boolean\"}]}");
        Node xml1 = xml("<config><ext>1</ext></config>");
        Node xml2 = xml("<config><ext>true</ext></config>");
        Node xml3 = xml("<config><ext>asdf</ext></config>");

        try {
            converter.convert(xml1).get(0).get("configuration");
            Assert.fail("Invalid config should not have been converted.");
        } catch (IllegalArgumentException __) {
            //good
        }
        ModelNode c2 = converter.convert(xml2).get(0).get("configuration");

        try {
            converter.convert(xml3).get(0).get("configuration");
            Assert.fail("Invalid configuration should not have been converted.");
        } catch (IllegalArgumentException __) {
            //good
        }

        Assert.assertNotNull(c2);
        Assert.assertEquals(ModelType.BOOLEAN, c2.getType());
        Assert.assertTrue(c2.asBoolean());
    }

    @Test
    public void testAnyOf() throws Exception {
        XmlToJson<Node> converter = converter("ext", "{\"anyOf\": [{\"type\": \"integer\"}, {\"type\": \"number\"}, {\"type\": \"boolean\"}]}");
        Node xml1 = xml("<config><ext>1</ext></config>");
        Node xml2 = xml("<config><ext>true</ext></config>");
        Node xml3 = xml("<config><ext>asdf</ext></config>");

        ModelNode c1 = converter.convert(xml1).get(0).get("configuration");
        ModelNode c2 = converter.convert(xml2).get(0).get("configuration");

        try {
            converter.convert(xml3).get(0).get("configuration");
            Assert.fail("Invalid configuration should not have been converted.");
        } catch (IllegalArgumentException __) {
            //good
        }

        Assert.assertNotNull(c1);
        Assert.assertNotNull(c2);

        Assert.assertEquals(ModelType.LONG, c1.getType());
        Assert.assertEquals(ModelType.BOOLEAN, c2.getType());
        Assert.assertEquals(1L, c1.asLong());
        Assert.assertTrue(c2.asBoolean());
    }

    @Test
    public void testAllOf() throws Exception {
        XmlToJson<Node> converter = converter("ext", "{\"allOf\": [{\"type\": \"integer\"}, {\"type\": \"number\"}]}");
        Node xml = xml("<config><ext>1</ext></config>");

        ModelNode c = converter.convert(xml).get(0).get("configuration");

        Assert.assertNotNull(c);

        Assert.assertEquals(ModelType.DOUBLE, c.getType());
        Assert.assertEquals(1D, c.asDouble(), 0);
    }

    private static Node xml(String xml) throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        return dBuilder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
    }

    private static ModelNode json(String json) {
        return ModelNode.fromJSONString(json);
    }

    private static XmlToJson<Node> converter(String extension, String schema) {
        Map<String, ModelNode> exts = new HashMap<>(1);
        exts.put(extension, json(schema));

        List<Short> nonChildrenNodeTypes = Arrays.asList(Node.TEXT_NODE, Node.CDATA_SECTION_NODE, Node.COMMENT_NODE);

        return new XmlToJson<>(exts,
                Node::getNodeName,
                n -> {
                    if (n.getChildNodes().getLength() == 1) {
                        Node textOrSomething = n.getFirstChild();
                        if (textOrSomething.getNodeType() == Node.TEXT_NODE ||
                                textOrSomething.getNodeType() == Node.CDATA_SECTION_NODE) {
                            return textOrSomething.getNodeValue();
                        }
                    }

                    return null;
                },
                (n, name) -> {
                    NamedNodeMap attrs = n.getAttributes();
                    if (attrs == null) {
                        return null;
                    }

                    Node attr = attrs.getNamedItem(name);
                    if (attr == null) {
                        return null;
                    }

                    return attr.getNodeValue();
                },
                n -> new NodeListList(n.getChildNodes()).stream()
                        .filter(x -> !nonChildrenNodeTypes.contains(x.getNodeType()))
                        .collect(Collectors.toList()));
    }

    private static final class NodeListList extends AbstractList<Node> {

        private final NodeList list;

        private NodeListList(NodeList list) {
            this.list = list;
        }

        @Override public Node get(int index) {
            return list.item(index);
        }

        @Override public int size() {
            return list.getLength();
        }
    }
}
