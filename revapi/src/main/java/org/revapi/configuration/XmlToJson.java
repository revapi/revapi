/*
 * Copyright 2014-2019 Lukas Krejci
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
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.revapi.Revapi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tool to convert some XML representation of the Revapi configuration to {@link ModelNode} used by Revapi.
 *
 * @param <Xml> The type of the xml representation used by the calling code.
 *
 * @author Lukas Krejci
 * @since 0.8.0
 */
public final class XmlToJson<Xml> {
    private static final Logger LOG = LoggerFactory.getLogger(XmlToJson.class);

    private final Function<Xml, String> getName;
    private final BiFunction<Xml, String, String> getAttributeValue;
    private final Function<Xml, String> getValue;
    private final Function<Xml, List<Xml>> getChildren;
    private final Map<String, ModelNode> knownExtensionSchemas;

    /**
     * Constructs a new instance using the schemas obtained from the provided Revapi instance.
     *
     * @param revapi the Revapi instance to extract the extension schemas from
     * @param getName a function to extract the name of a node
     * @param getValue a function to extract the text value of a node
     * @param getAttributeValue a function to extract the value of an attribute with given name from a node
     * @param getChildren a function to extract children. The list must ONLY include child nodes, not the "virtual" text
     *                    and attribute nodes.
     */
    public XmlToJson(Revapi revapi, Function<Xml, String> getName, Function<Xml, String> getValue,
                     BiFunction<Xml, String, String> getAttributeValue, Function<Xml, List<Xml>> getChildren) {
        this(getKnownExtensionSchemas(revapi), getName, getValue, getAttributeValue, getChildren);
    }

    /**
     * Constructs a new instance using the provided schemas.
     *
     * @param knownExtensionSchemas the schemas to use when trying to convert XML to JSON
     * @param getName a function to extract the name of a node
     * @param getValue a function to extract the text value of a node
     * @param getAttributeValue a function to extract the value of an attribute with given name from a node
     * @param getChildren a function to extract children. The list must ONLY include child nodes, not the "virtual" text
     *                    and attribute nodes.
     */
    public XmlToJson(Map<String, ModelNode> knownExtensionSchemas, Function<Xml, String> getName,
              Function<Xml, String> getValue, BiFunction<Xml, String, String> getAttributeValue,
              Function<Xml, List<Xml>> getChildren) {
        this.getName = getName;
        this.getValue = getValue;
        this.getAttributeValue = getAttributeValue;
        this.getChildren = getChildren;
        this.knownExtensionSchemas = knownExtensionSchemas;
    }

    public ModelNode convert(Xml xml) {
        ModelNode fullConfiguration = new ModelNode();
        fullConfiguration.setEmptyList();

        for (Xml c : getChildren.apply(xml)) {

            String extensionId = getName.apply(c);
            String id = getAttributeValue.apply(c, "id");
            ModelNode schema = knownExtensionSchemas.get(extensionId);
            if (schema == null) {
                LOG.warn("Extension '" + extensionId +
                        "' doesn't declare a JSON schema but XML contains its configuration. Cannot convert it into" +
                        " JSON and will ignore it!");
                continue;
            }

            ModelNode config = convert(c, schema);

            ModelNode instanceConfig = new ModelNode();
            instanceConfig.get("extension").set(extensionId);
            if (id != null) {
                instanceConfig.get("id").set(id);
            }
            instanceConfig.get("configuration").set(config);

            fullConfiguration.add(instanceConfig);
        }

        return fullConfiguration;
    }

    private ModelNode convert(Xml configuration, ModelNode jsonSchema) {
        return convert(configuration, jsonSchema, jsonSchema);
    }

    private ModelNode convert(Xml configuration, ModelNode jsonSchema, ModelNode rootSchema) {
        ModelNode typeNode = jsonSchema.get("type");
        if (!typeNode.isDefined()) {
            ModelNode ret = null;
            if (jsonSchema.get("enum").isDefined()) {
                ret = convertByEnum(configuration, jsonSchema.get("enum"));
            } else if (jsonSchema.get("$ref").isDefined()) {
                jsonSchema = findRef(rootSchema, jsonSchema.get("$ref").asString());
                ret = convert(configuration, jsonSchema, rootSchema);
            } else if (jsonSchema.hasDefined("oneOf")) {
                ret = convertByOneOf(configuration, jsonSchema.get("oneOf").asList());
            } else if (jsonSchema.hasDefined("anyOf")) {
                ret = convertByAnyOf(configuration, jsonSchema.get("anyOf").asList());
            } else if (jsonSchema.hasDefined("allOf")) {
                ret = convertByAllOf(configuration, jsonSchema.get("allOf").asList());
            }

            if (ret == null) {
                throw new IllegalArgumentException("Could not convert the configuration. Schema:\n"
                        + jsonSchema.toJSONString(false)
                + "\n\nData:\n" + configuration);
            }

            return ret;
        }

        if (typeNode.getType() != ModelType.STRING) {
            throw new IllegalArgumentException(
                    "JSON schema allows for multiple possible types. " +
                            "This is not supported by the XML-to-JSON conversion yet. Schema:\n"
                            + jsonSchema.toJSONString(false));
        }

        String type = typeNode.asString();

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

    private static ModelNode findRef(ModelNode rootSchema, String ref) {
        return JSONPointer.parse(ref).navigate(rootSchema);
    }

    private ModelNode convertByEnum(Xml configuration, ModelNode enumValues) {
        String xmlValue = getValue.apply(configuration);
        Map<String, ModelType> jsonValues = enumValues.asList().stream()
                .collect(Collectors.toMap(ModelNode::asString, ModelNode::getType));

        for (Map.Entry<String, ModelType> e : jsonValues.entrySet()) {
            String jsonValue = e.getKey();
            ModelType jsonType = e.getValue();

            if (Objects.equals(xmlValue, jsonValue)) {
                switch (jsonType) {
                    case BIG_INTEGER:
                    case INT:
                    case LONG:
                        return new ModelNode(Long.parseLong(xmlValue));
                    case BIG_DECIMAL:
                    case DOUBLE:
                        return new ModelNode(Double.parseDouble(xmlValue));
                    case BOOLEAN:
                        return new ModelNode(Boolean.parseBoolean(xmlValue));
                    case STRING:
                        return new ModelNode(xmlValue);
                    default:
                        throw new IllegalArgumentException(
                                "Unsupported type of enum value defined in schema: " + jsonValue);
                }
            }
        }

        throw new IllegalArgumentException(
                "XML value '" + xmlValue + " doesn't match any of the allowed: " + jsonValues.keySet());
    }

    private ModelNode convertObject(Xml configuration, ModelNode jsonSchema, ModelNode rootSchema) {
        if (getValue.apply(configuration) != null) {
            // object cannot contain text nodes.. or rather we don't support that
            throw new IllegalArgumentException("Converting an XML node with text (and possibly children) to JSON" +
                    " object is not supported.");
        }

        ModelNode object = new ModelNode();
        object.setEmptyObject();
        ModelNode propertySchemas = jsonSchema.get("properties");
        ModelNode additionalPropSchemas = jsonSchema.get("additionalProperties");
        for (Xml childConfig : getChildren.apply(configuration)) {
            String name = getName.apply(childConfig);
            ModelNode childSchema = propertySchemas.get(name);
            if (!childSchema.isDefined()) {
                if (additionalPropSchemas.getType() == ModelType.BOOLEAN) {
                    throw new IllegalArgumentException("Cannot determine the format for the '" + name +
                            "' XML tag during the XML-to-JSON conversion.");
                }
                childSchema = additionalPropSchemas;
            }

            if (!childSchema.isDefined()) {
                throw new IllegalArgumentException("Could not determine the format for the '" + name +
                        "' XML tag during the XML-to-JSON conversion.");
            }
            ModelNode jsonChild = convert(childConfig, childSchema, rootSchema);
            object.get(name).set(jsonChild);
        }
        return object;
    }

    private ModelNode convertArray(Xml configuration, ModelNode jsonSchema, ModelNode rootSchema) {
        ModelNode itemsSchema = jsonSchema.get("items");
        if (!itemsSchema.isDefined()) {
            throw new IllegalArgumentException(
                    "No schema found for items of a list. Cannot continue with XML-to-JSON conversion.");
        }
        if (getValue.apply( configuration ) != null) {
            throw new IllegalArgumentException("Array is not allowed to have a text node");
        }
        ModelNode list = new ModelNode();
        list.setEmptyList();
        for (Xml childConfig : getChildren.apply(configuration)) {
            ModelNode child = convert(childConfig, itemsSchema, rootSchema);
            list.add(child);
        }
        return list;
    }

    private ModelNode convertString(Xml configuration) {
        return new ModelNode(getValue.apply(configuration));
    }

    private ModelNode convertNumber(Xml configuration) {
        try {
            double floatVal = Double.parseDouble(getValue.apply(configuration));
            return new ModelNode(floatVal);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value.", e);
        }
    }

    private ModelNode convertInteger(Xml configuration) {
        try {
            long intVal = Long.parseLong(getValue.apply(configuration));
            return new ModelNode(intVal);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value.", e);
        }
    }

    private ModelNode convertBoolean(Xml configuration) {
        String v = getValue.apply(configuration);
        Boolean boolVal = "true".equalsIgnoreCase(v)
                ? Boolean.TRUE
                : ("false".equalsIgnoreCase(v) ? Boolean.FALSE : null);
        if (boolVal == null) {
            throw new IllegalArgumentException("'true' or 'false' expected as a boolean value.");
        }
        return new ModelNode(boolVal);
    }

    private ModelNode convertByOneOf(Xml configuration, Iterable<ModelNode> candidateSchemas) {
        boolean matched = false;
        ModelNode parsed = null;
        for (ModelNode candidateSchema : candidateSchemas) {
            try {
                parsed = convert(configuration, candidateSchema);
                if (matched) {
                    return null;
                } else {
                    matched = true;
                }
            } catch (IllegalArgumentException __) {
                //continue
            }
        }

        return parsed;
    }

    private ModelNode convertByAnyOf(Xml configuration, Iterable<ModelNode> candidateSchemas) {
        for (ModelNode candidateSchema : candidateSchemas) {
            try {
                return convert(configuration, candidateSchema);
            } catch (IllegalArgumentException __) {
                //continue
            }
        }

        return null;
    }

    private ModelNode convertByAllOf(Xml configuration, Iterable<ModelNode> candidateSchemas) {
        ModelNode parsed = null;
        for (ModelNode candidateSchema : candidateSchemas) {
            try {
                parsed = convert(configuration, candidateSchema);
            } catch (IllegalArgumentException __) {
                return null;
            }
        }

        return parsed;
    }

    //heavily inspired by the implementation in org.json.JSONPointer
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
            for (String token: tokens) {
                ret.append('/').append(escape(token));
            }
            return ret.toString();
        }

        private static String unescape(String token) {
            return token
                    .replace("~1", "/")
                    .replace("~0", "~")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }

        private static String escape(String token) {
            return token
                    .replace("~", "~0")
                    .replace("/", "~1")
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
        }
    }

    private static Map<String, ModelNode> getKnownExtensionSchemas(Revapi revapi) {
        Map<String, ModelNode> knownSchemas = new HashMap<>();
        extractKnownSchemas(knownSchemas, revapi.getPipelineConfiguration().getApiAnalyzerTypes());
        extractKnownSchemas(knownSchemas, revapi.getPipelineConfiguration().getTransformTypes());
        extractKnownSchemas(knownSchemas, revapi.getPipelineConfiguration().getTreeFilterTypes());
        extractKnownSchemas(knownSchemas, revapi.getPipelineConfiguration().getReporterTypes());

        return knownSchemas;
    }

    private static <T extends Configurable>
    void extractKnownSchemas(Map<String, ModelNode> schemaByExtensionId, Set<Class<? extends T>> types) {
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
                ModelNode schemaNode = ModelNode.fromJSONString(schemaString);

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
