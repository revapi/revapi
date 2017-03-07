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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * This is an attempt at driving XML-to-JSON conversion by a JSON schema. The more "exotic" parts of the JSON schema
 * spec (like patternProperties or multiple possible types of a value) are not supported.
 *
 * @author Lukas Krejci
 * @since 0.9.0
 */
final class SchemaDrivenXmlToJSONConverter {

    private SchemaDrivenXmlToJSONConverter() {

    }

    static ModelNode convert(PlexusConfiguration configuration, ModelNode jsonSchema) {
        return convert(configuration, jsonSchema, jsonSchema);
    }

    private static ModelNode convert(PlexusConfiguration configuration, ModelNode jsonSchema, ModelNode rootSchema) {
        ModelNode type = jsonSchema.get("type");
        if (!type.isDefined()) {
            if (jsonSchema.get("enum").isDefined()) {
                return convertByEnum(configuration, jsonSchema.get("enum"));
            } else if (jsonSchema.get("$ref").isDefined()) {
                jsonSchema = findRef(rootSchema, jsonSchema.get("$ref").asString());
                return convert(configuration, jsonSchema, rootSchema);
            }
        }

        if (type.getType() != ModelType.STRING) {
            throw new IllegalArgumentException(
                    "JSON schema allows for multiple possible types. " +
                            "This is not supported by the XML-to-JSON conversion yet.");
        }

        String valueType = type.asString();

        if (valueType == null) {
            throw new IllegalArgumentException(
                    "JSON schema type deduction and 'oneOf', 'anyOf' or 'allOf' not supported.");
        }

        switch (valueType) {
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
                throw new IllegalArgumentException("Unsupported json value type: " + valueType);
        }
    }

    private static ModelNode findRef(ModelNode rootSchema, String ref) {
        return JSONPointer.parse(ref).navigate(rootSchema);
    }

    private static ModelNode convertByEnum(PlexusConfiguration configuration, ModelNode enumValues) {
        String xmlValue = configuration.getValue();
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

    private static ModelNode convertObject(PlexusConfiguration configuration, ModelNode jsonSchema, ModelNode rootSchema) {
        ModelNode object = new ModelNode();
        object.setEmptyObject();
        ModelNode propertySchemas = jsonSchema.get("properties");
        ModelNode additionalPropSchemas = jsonSchema.get("additionalProperties");
        for (PlexusConfiguration childConfig : configuration.getChildren()) {
            String name = childConfig.getName();
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

    private static ModelNode convertArray(PlexusConfiguration configuration, ModelNode jsonSchema, ModelNode rootSchema) {
        ModelNode itemsSchema = jsonSchema.get("items");
        if (!itemsSchema.isDefined()) {
            throw new IllegalArgumentException(
                    "No schema found for items of a list. Cannot continue with XML-to-JSON conversion.");
        }
        ModelNode list = new ModelNode();
        list.setEmptyList();
        for (PlexusConfiguration childConfig : configuration.getChildren()) {
            ModelNode child = convert(childConfig, itemsSchema, rootSchema);
            list.add(child);
        }
        return list;
    }

    private static ModelNode convertString(PlexusConfiguration configuration) {
        return new ModelNode(configuration.getValue());
    }

    private static ModelNode convertNumber(PlexusConfiguration configuration) {
        try {
            double floatVal = Double.parseDouble(configuration.getValue());
            return new ModelNode(floatVal);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value.", e);
        }
    }

    private static ModelNode convertInteger(PlexusConfiguration configuration) {
        try {
            long intVal = Long.parseLong(configuration.getValue());
            return new ModelNode(intVal);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value.", e);
        }
    }

    private static ModelNode convertBoolean(PlexusConfiguration configuration) {
        String v = configuration.getValue();
        Boolean boolVal = "true".equalsIgnoreCase(v)
                ? Boolean.TRUE
                : ("false".equalsIgnoreCase(v) ? Boolean.FALSE : null);
        if (boolVal == null) {
            throw new IllegalArgumentException("'true' or 'false' expected as a boolean value.");
        }
        return new ModelNode(boolVal);
    }

    //heavily inspired by the implementation in org.json.JSONPointer
    static final class JSONPointer {
        private final List<String> tokens;

        static JSONPointer parse(String pointer) {
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
}
