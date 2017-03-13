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

import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.revapi.configuration.XmlToJson;

/**
 * Converts JSON representation into a XML.
 *
 * @author Lukas Krejci
 * @since 0.9.0
 */
final class SchemaDrivenJSONToXmlConverter {
    private SchemaDrivenJSONToXmlConverter() {

    }

    static PlexusConfiguration convert(ModelNode configuration, ModelNode jsonSchema, String extensionId) {
        return convert(configuration, jsonSchema, jsonSchema, extensionId);
    }

    private static PlexusConfiguration convert(ModelNode configuration, ModelNode jsonSchema, ModelNode rootSchema, String tagName) {
        ModelNode type = jsonSchema.get("type");
        if (!type.isDefined()) {
            if (jsonSchema.get("enum").isDefined()) {
                boolean containsUnsupportedTypes = jsonSchema.get("enum").asList().stream()
                        .anyMatch(n -> n.getType() == ModelType.OBJECT || n.getType() == ModelType.LIST);

                if (containsUnsupportedTypes) {
                    throw new IllegalArgumentException("Unsupported type of enum value defined in schema.");
                }
                return convertSimple(tagName, configuration.asString());
            } else if (jsonSchema.get("$ref").isDefined()) {
                jsonSchema = findRef(rootSchema, jsonSchema.get("$ref").asString());
                return convert(configuration, jsonSchema, rootSchema, tagName);
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
                return convertSimple(tagName, configuration.asString());
            case "integer":
                return convertSimple(tagName, configuration.asString());
            case "number":
                return convertSimple(tagName, configuration.asString());
            case "string":
                return convertSimple(tagName, configuration.asString());
            case "array":
                return convertArray(configuration, jsonSchema, rootSchema, tagName);
            case "object":
                return convertObject(configuration, jsonSchema, rootSchema, tagName);
            default:
                throw new IllegalArgumentException("Unsupported json value type: " + valueType);
        }
    }

    private static ModelNode findRef(ModelNode rootSchema, String ref) {
        return XmlToJson.JSONPointer.parse(ref).navigate(rootSchema);
    }

    private static PlexusConfiguration convertObject(ModelNode configuration, ModelNode jsonSchema, ModelNode rootSchema, String tagName) {
        XmlPlexusConfiguration object = new XmlPlexusConfiguration(tagName);

        ModelNode propertySchemas = jsonSchema.get("properties");
        ModelNode additionalPropSchemas = jsonSchema.get("additionalProperties");
        for (String key : configuration.keys()) {
            ModelNode childConfig = configuration.get(key);
            ModelNode childSchema = propertySchemas.get(key);
            if (!childSchema.isDefined()) {
                if (additionalPropSchemas.getType() == ModelType.BOOLEAN) {
                    throw new IllegalArgumentException("Cannot determine the format for the '" + key +
                            "' JSON value during the JSON-to-XML conversion.");
                }
                childSchema = additionalPropSchemas;
            }

            if (!childSchema.isDefined()) {
                throw new IllegalArgumentException("Could not determine the format for the '" + key +
                        "' JSON value during the JSON-to-XML conversion.");
            }
            PlexusConfiguration xmlChild = convert(childConfig, childSchema, rootSchema, key);
            object.addChild(xmlChild);
        }
        return object;
    }

    private static PlexusConfiguration convertArray(ModelNode configuration, ModelNode jsonSchema, ModelNode rootSchema, String tagName) {
        ModelNode itemsSchema = jsonSchema.get("items");
        if (!itemsSchema.isDefined()) {
            throw new IllegalArgumentException(
                    "No schema found for items of a list. Cannot continue with XML-to-JSON conversion.");
        }
        PlexusConfiguration list = new XmlPlexusConfiguration(tagName);
        for (ModelNode childConfig : configuration.asList()) {
            PlexusConfiguration child = convert(childConfig, itemsSchema, rootSchema, "item");
            list.addChild(child);
        }
        return list;
    }

    private static PlexusConfiguration convertSimple(String tagName, String value) {
        XmlPlexusConfiguration ret = new XmlPlexusConfiguration(tagName);
        ret.setValue(value);
        return ret;
    }
}
