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
package org.revapi.configuration;

import static java.util.stream.Collectors.toMap;

import static org.revapi.configuration.JSONUtil.isNullOrUndefined;

import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.revapi.Revapi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tool to convert some XML representation of the Revapi configuration to {@link JsonNode} used by Revapi.
 *
 * @param <Xml>
 *            The type of the xml representation used by the calling code.
 *
 * @author Lukas Krejci
 * 
 * @since 0.8.0
 */
public final class XmlToJson<Xml> {
    private static final Logger LOG = LoggerFactory.getLogger(XmlToJson.class);

    private final Function<Xml, String> getName;
    private final BiFunction<Xml, String, String> getAttributeValue;
    private final Function<Xml, String> getValue;
    private final Function<Xml, List<Xml>> getChildren;
    private final Map<String, JsonNode> knownExtensionSchemas;

    /**
     * A convenience constructor to create an instance using the extension schemas known to the provided Revapi
     * instance.
     *
     * @see #XmlToJson(Map, Function, Function, BiFunction, Function)
     * 
     * @deprecated use #fromRevapi
     */
    @Deprecated
    public XmlToJson(Revapi revapi, Function<Xml, String> getName, Function<Xml, String> getValue,
            BiFunction<Xml, String, String> getAttributeValue, Function<Xml, List<Xml>> getChildren) {
        this(getKnownExtensionSchemas(revapi), getName, getValue, getAttributeValue, getChildren, 42);
    }

    /**
     * Constructs a new XML to JSON converter. To be able to navigate the XML and to convert to JSON data types
     * correctly, the instance needs to know about the schemas used by various extensions.
     *
     * @param knownExtensionSchemas
     *            the schemas of the known extensions. Keys are extension ids, values are extension schemas
     * @param getName
     *            a function that gets the name of an XML tag
     * @param getValue
     *            a function that gets the textual value of an XML node, e.g. it's textual content.
     * @param getAttributeValue
     *            a function to get a value of an attribute of an XML node
     * @param getChildren
     *            a function that gets the children of an XML node. Note that the returned list MUST NOT contain any
     *            text or CDATA nodes - those are to be used in the {@code getValue} function. It also MUST NOT contain
     *            any comment nodes.
     * 
     * @deprecated use the Jackson-based variant
     */
    @Deprecated
    public XmlToJson(Map<String, ModelNode> knownExtensionSchemas, Function<Xml, String> getName,
            Function<Xml, String> getValue, BiFunction<Xml, String, String> getAttributeValue,
            Function<Xml, List<Xml>> getChildren) {
        this(knownExtensionSchemas.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> JSONUtil.convert(e.getValue()))), getName, getValue,
                getAttributeValue, getChildren, 42);
    }

    /**
     * Constructs a new XML to JSON converter. To be able to navigate the XML and to convert to JSON data types
     * correctly, the instance needs to know about the schemas used by various extensions.
     *
     * @param knownExtensionSchemas
     *            the schemas of the known extensions. Keys are extension ids, values are extension schemas
     * @param getName
     *            a function that gets the name of an XML tag
     * @param getValue
     *            a function that gets the textual value of an XML node, e.g. it's textual content.
     * @param getAttributeValue
     *            a function to get a value of an attribute of an XML node
     * @param getChildren
     *            a function that gets the children of an XML node. Note that the returned list MUST NOT contain any
     *            text or CDATA nodes - those are to be used in the {@code getValue} function. It also MUST NOT contain
     *            any comment nodes.
     */
    public static <Xml> XmlToJson<Xml> fromKnownSchemas(Map<String, JsonNode> knownExtensionSchemas,
            Function<Xml, String> getName, Function<Xml, String> getValue,
            BiFunction<Xml, String, String> getAttributeValue, Function<Xml, List<Xml>> getChildren) {
        return new XmlToJson<>(knownExtensionSchemas, getName, getValue, getAttributeValue, getChildren, 42);
    }

    public static <Xml> XmlToJson<Xml> fromRevapi(Revapi revapi, Function<Xml, String> getName,
            Function<Xml, String> getValue, BiFunction<Xml, String, String> getAttributeValue,
            Function<Xml, List<Xml>> getChildren) {
        return fromKnownSchemas(getKnownExtensionSchemas(revapi), getName, getValue, getAttributeValue, getChildren);
    }

    // dummy just to distinguish this from the deprecated constructor using the map of ModelNode based extension
    // schemas.
    private XmlToJson(Map<String, JsonNode> knownExtensionSchemas, Function<Xml, String> getName,
            Function<Xml, String> getValue, BiFunction<Xml, String, String> getAttributeValue,
            Function<Xml, List<Xml>> getChildren, int dummy) {
        this.getName = getName;
        this.getValue = getValue;
        this.getAttributeValue = getAttributeValue;
        this.getChildren = getChildren;
        this.knownExtensionSchemas = knownExtensionSchemas;
    }

    /**
     * @deprecated use {@link #convertXml(Object)} instead
     */
    @Deprecated
    public ModelNode convert(Xml xml) {
        return JSONUtil.convert(convertXml(xml));
    }

    public JsonNode convertXml(Xml xml) {
        ArrayNode fullConfiguration = JsonNodeFactory.instance.arrayNode();

        for (Xml c : getChildren.apply(xml)) {

            String extensionId = getName.apply(c);
            String id = getAttributeValue.apply(c, "id");
            JsonNode schema = knownExtensionSchemas.get(extensionId);
            if (schema == null) {
                LOG.warn("Extension '" + extensionId
                        + "' doesn't declare a JSON schema but XML contains its configuration. Cannot convert it into"
                        + " JSON and will ignore it!");
                continue;
            }

            JsonNode config = convert(c, schema, schema);

            ObjectNode instanceConfig = JsonNodeFactory.instance.objectNode();
            instanceConfig.put("extension", extensionId);
            if (id != null) {
                instanceConfig.put("id", id);
            }
            instanceConfig.set("configuration", config);

            fullConfiguration.add(instanceConfig);
        }

        return fullConfiguration;
    }

    private JsonNode convert(Xml configuration, JsonNode jsonSchema, JsonNode rootSchema) {
        JsonNode typeNode = jsonSchema.get("type");
        if (isNullOrUndefined(typeNode)) {
            JsonNode ret = null;
            if (jsonSchema.hasNonNull("enum")) {
                ret = convertByEnum(configuration, jsonSchema.get("enum"));
            } else if (jsonSchema.hasNonNull("$ref")) {
                jsonSchema = findRef(rootSchema, jsonSchema.get("$ref").asText());
                ret = convert(configuration, jsonSchema, rootSchema);
            } else if (jsonSchema.hasNonNull("oneOf")) {
                ret = convertByOneOf(configuration, jsonSchema.get("oneOf"), rootSchema);
            } else if (jsonSchema.hasNonNull("anyOf")) {
                ret = convertByAnyOf(configuration, jsonSchema.get("anyOf"), rootSchema);
            } else if (jsonSchema.hasNonNull("allOf")) {
                ret = convertByAllOf(configuration, jsonSchema.get("allOf"), rootSchema);
            }

            if (ret == null) {
                throw new IllegalArgumentException("Could not convert the configuration. Schema:\n"
                        + JSONUtil.toString(jsonSchema) + "\n\nData:\n" + configuration);
            }

            return ret;
        }

        if (typeNode.getNodeType() != JsonNodeType.STRING) {
            throw new IllegalArgumentException("JSON schema allows for multiple possible types. "
                    + "This is not supported by the XML-to-JSON conversion yet. Schema:\n"
                    + JSONUtil.toString(jsonSchema));
        }

        String type = typeNode.asText();

        switch (type) {
        case "boolean":
            return convertBoolean(configuration);
        case "integer":
            return convertInteger(configuration);
        case "number":
            return convertNumber(configuration);
        case "string":
            return convertString(configuration);
        case "array":
            return convertArray(configuration, jsonSchema, rootSchema);
        case "object":
            return convertObject(configuration, jsonSchema, rootSchema);
        default:
            throw new IllegalArgumentException("Unsupported json value type: " + type);
        }
    }

    private static JsonNode findRef(JsonNode rootSchema, String ref) {
        if (ref.startsWith("#")) {
            ref = ref.substring(1);
        }
        return rootSchema.at(ref);
    }

    private JsonNode convertByEnum(Xml configuration, Iterable<JsonNode> enumValues) {
        String xmlValue = getValue.apply(configuration);

        for (JsonNode jsonValue : enumValues) {
            String txt = jsonValue.asText();
            if (txt.equals(xmlValue)) {
                return jsonValue.deepCopy();
            }
        }

        throw new IllegalArgumentException(
                "XML value '" + xmlValue + " doesn't match any of the allowed: " + enumValues);
    }

    private JsonNode convertObject(Xml configuration, JsonNode jsonSchema, JsonNode rootSchema) {
        if (getValue.apply(configuration) != null) {
            // object cannot contain text nodes.. or rather we don't support that
            throw new IllegalArgumentException(
                    "Converting an XML node with text (and possibly children) to JSON" + " object is not supported.");
        }

        ObjectNode object = JsonNodeFactory.instance.objectNode();

        JsonNode propertySchemas = jsonSchema.get("properties");
        JsonNode additionalPropSchemas = jsonSchema.get("additionalProperties");
        for (Xml childConfig : getChildren.apply(configuration)) {
            String name = getName.apply(childConfig);
            JsonNode childSchema = propertySchemas == null ? null : propertySchemas.get(name);
            if (childSchema == null) {
                if (additionalPropSchemas != null && additionalPropSchemas.getNodeType() == JsonNodeType.BOOLEAN) {
                    throw new IllegalArgumentException("Cannot determine the format for the '" + name
                            + "' XML tag during the XML-to-JSON conversion.");
                }
                childSchema = additionalPropSchemas;
            }

            if (childSchema != null) {
                JsonNode jsonChild = convert(childConfig, childSchema, rootSchema);
                object.set(name, jsonChild);
            }
        }
        return object;
    }

    private ArrayNode convertArray(Xml configuration, JsonNode jsonSchema, JsonNode rootSchema) {
        JsonNode itemsSchema = jsonSchema.get("items");
        if (itemsSchema == null) {
            throw new IllegalArgumentException(
                    "No schema found for items of a list. Cannot continue with XML-to-JSON conversion.");
        }

        String value = getValue.apply(configuration);

        if (value != null && !value.trim().isEmpty()) {
            throw new IllegalArgumentException("<" + getName.apply(configuration)
                    + "> should represent an array of values, but a textual value was found.");
        }

        ArrayNode list = JsonNodeFactory.instance.arrayNode();

        for (Xml childConfig : getChildren.apply(configuration)) {
            JsonNode child = convert(childConfig, itemsSchema, rootSchema);
            list.add(child);
        }
        return list;
    }

    private JsonNode convertString(Xml configuration) {
        String val = getValue.apply(configuration);
        if (val == null) {
            throw new IllegalArgumentException("Null string not allowed.");
        }
        return JsonNodeFactory.instance.textNode(val);
    }

    private JsonNode convertNumber(Xml configuration) {
        try {
            double floatVal = Double.parseDouble(getValue.apply(configuration));
            return JsonNodeFactory.instance.numberNode(floatVal);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value.", e);
        }
    }

    private JsonNode convertInteger(Xml configuration) {
        try {
            long intVal = Long.parseLong(getValue.apply(configuration));
            return JsonNodeFactory.instance.numberNode(intVal);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value.", e);
        }
    }

    private JsonNode convertBoolean(Xml configuration) {
        String v = getValue.apply(configuration);
        Boolean boolVal = "true".equalsIgnoreCase(v) ? Boolean.TRUE
                : ("false".equalsIgnoreCase(v) ? Boolean.FALSE : null);
        if (boolVal == null) {
            throw new IllegalArgumentException("'true' or 'false' expected as a boolean value.");
        }
        return JsonNodeFactory.instance.booleanNode(boolVal);
    }

    private JsonNode convertByOneOf(Xml configuration, Iterable<JsonNode> candidateSchemas, JsonNode rootSchema) {
        boolean matched = false;
        JsonNode parsed = null;
        for (JsonNode candidateSchema : candidateSchemas) {
            try {
                parsed = convert(configuration, candidateSchema, rootSchema);
                if (matched) {
                    return null;
                } else {
                    matched = true;
                }
            } catch (IllegalArgumentException __) {
                // continue
            }
        }

        return parsed;
    }

    private JsonNode convertByAnyOf(Xml configuration, Iterable<JsonNode> candidateSchemas, JsonNode rootSchema) {
        for (JsonNode candidateSchema : candidateSchemas) {
            try {
                return convert(configuration, candidateSchema, rootSchema);
            } catch (IllegalArgumentException __) {
                // continue
            }
        }

        return null;
    }

    private JsonNode convertByAllOf(Xml configuration, Iterable<JsonNode> candidateSchemas, JsonNode rootSchema) {
        JsonNode parsed = null;
        for (JsonNode candidateSchema : candidateSchemas) {
            try {
                JsonNode newParsed = convert(configuration, candidateSchema, rootSchema);
                if (parsed == null) {
                    parsed = newParsed;
                } else {
                    // merge the newly parsed data into the already parsed data
                    if (parsed.getNodeType() != newParsed.getNodeType()) {
                        return null;
                    }

                    switch (parsed.getNodeType()) {
                    case ARRAY:
                        for (JsonNode item : newParsed) {
                            ((ArrayNode) parsed).add(item);
                        }
                        break;
                    case OBJECT:
                        Iterator<Map.Entry<String, JsonNode>> fields = newParsed.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> field = fields.next();
                            ((ObjectNode) parsed).put(field.getKey(), field.getValue());
                        }
                        break;
                    default:
                        parsed = newParsed;
                        break;
                    }
                }
            } catch (IllegalArgumentException __) {
                return null;
            }
        }

        return parsed;
    }

    /**
     * @deprecated use Jackson's impl
     */
    // heavily inspired by the implementation in org.json.JSONPointer
    @Deprecated
    public static final class JSONPointer {
        private final List<String> tokens;

        public static JSONPointer parse(String pointer) {
            if (pointer.isEmpty() || pointer.equals("#")) {
                return new JSONPointer(Collections.emptyList());
            }

            if (pointer.startsWith("#/")) {
                pointer = pointer.substring(2);
                try {
                    pointer = URLDecoder.decode(pointer, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalStateException("UTF-8 not supported. What?");
                }
            } else if (pointer.startsWith("/")) {
                pointer = pointer.substring(1);
            } else {
                throw new IllegalArgumentException("a JSON pointer should start with '/' or '#/'");
            }

            List<String> tokens = new ArrayList<>();
            for (String token : pointer.split("/")) {
                tokens.add(unescape(token));
            }

            return new JSONPointer(tokens);
        }

        private JSONPointer(List<String> tokens) {
            this.tokens = tokens;
        }

        public ModelNode navigate(ModelNode root) {
            if (tokens.isEmpty()) {
                return root;
            }

            ModelNode current = root;
            for (String token : tokens) {
                if (current.getType() == ModelType.OBJECT) {
                    current = current.get(unescape(token));
                } else if (current.getType() == ModelType.LIST) {
                    int idx = Integer.parseInt(token);
                    current = current.get(idx);
                } else {
                    throw new IllegalArgumentException("Cannot navigate to '" + this + "' in " + root);
                }
            }
            return current;
        }

        @Override
        public String toString() {
            StringBuilder ret = new StringBuilder();
            for (String token : tokens) {
                ret.append('/').append(escape(token));
            }
            return ret.toString();
        }

        private static String unescape(String token) {
            return token.replace("~1", "/").replace("~0", "~").replace("\\\"", "\"").replace("\\\\", "\\");
        }

        private static String escape(String token) {
            return token.replace("~", "~0").replace("/", "~1").replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    private static Map<String, JsonNode> getKnownExtensionSchemas(Revapi revapi) {
        Map<String, JsonNode> knownSchemas = new HashMap<>();
        extractKnownSchemas(knownSchemas, revapi.getPipelineConfiguration().getApiAnalyzerTypes());
        extractKnownSchemas(knownSchemas, revapi.getPipelineConfiguration().getTransformTypes());
        extractKnownSchemas(knownSchemas, revapi.getPipelineConfiguration().getTreeFilterTypes());
        extractKnownSchemas(knownSchemas, revapi.getPipelineConfiguration().getReporterTypes());

        return knownSchemas;
    }

    private static <T extends Configurable> void extractKnownSchemas(Map<String, JsonNode> schemaByExtensionId,
            Set<Class<? extends T>> types) {
        for (Class<? extends T> extensionType : types) {
            try {
                Configurable c = extensionType.newInstance();
                String extensionId = c.getExtensionId();
                if (extensionId == null) {
                    continue;
                }

                Reader schema = c.getJSONSchema();
                if (schema == null) {
                    continue;
                }

                String schemaString = readFull(schema);
                JsonNode schemaNode = JSONUtil.parse(schemaString);

                schemaByExtensionId.put(extensionId, schemaNode);
            } catch (InstantiationException | IllegalAccessException e) {
                throw new IllegalStateException("Extension " + extensionType + " is not default-constructable.");
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to read the schema of extension " + extensionType);
            }
        }
    }

    private static String readFull(Reader reader) throws IOException {
        char[] buffer = new char[512];
        StringBuilder bld = new StringBuilder();
        int cnt;
        while ((cnt = reader.read(buffer)) != -1) {
            bld.append(buffer, 0, cnt);
        }

        return bld.toString();
    }
}
