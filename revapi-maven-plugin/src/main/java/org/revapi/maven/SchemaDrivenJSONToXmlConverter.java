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

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.revapi.configuration.ConfigurationValidator;
import org.revapi.configuration.XmlToJson;

/**
 * Converts JSON representation into a XML.
 *
 * @author Lukas Krejci
 * @since 0.9.0
 */
final class SchemaDrivenJSONToXmlConverter {

    private static final ConfigurationValidator VALIDATOR = new ConfigurationValidator();

    private SchemaDrivenJSONToXmlConverter() {

    }

    /**
     * Converts the given {@code jsonConfig} into XML given the JSON schemas for the extensions.
     * <p>
     * Beware that {@link PlexusConfiguration} does NOT do any escaping of the values. Use
     * {@link XmlUtil#toIndentedString(PlexusConfiguration, int, int, Writer)} to correctly serialize the configuration.
     * 
     * @param extensionSchemas the known extension schemas
     * @param jsonConfig the json configuration to convert
     * @return the {@link PlexusConfiguration} instance
     * @throws IOException on error
     */
    static PlexusConfiguration convertToXml(Map<String, ModelNode> extensionSchemas, ModelNode jsonConfig)
            throws IOException {
        if (jsonConfig.getType() == ModelType.LIST) {
            return convertNewStyleConfigToXml(extensionSchemas, jsonConfig);
        } else {
            return convertOldStyleConfigToXml(extensionSchemas, jsonConfig);
        }
    }

    //visibility increased for testing
    static PlexusConfiguration convert(ModelNode configuration, ModelNode jsonSchema, String extensionId, String id) {
        ConversionContext ctx = new ConversionContext();
        ctx.currentSchema = jsonSchema;
        ctx.rootSchema = jsonSchema;
        ctx.pushTag(extensionId);
        ctx.id = id;
        return convert(configuration, ctx);
    }

    private static PlexusConfiguration convert(ModelNode configuration, ConversionContext ctx) {
        ModelNode type = ctx.currentSchema.get("type");
        if (!type.isDefined()) {
            if (ctx.currentSchema.get("enum").isDefined()) {
                boolean containsUnsupportedTypes = ctx.currentSchema.get("enum").asList().stream()
                        .anyMatch(n -> n.getType() == ModelType.OBJECT || n.getType() == ModelType.LIST);

                if (containsUnsupportedTypes) {
                    throw new IllegalArgumentException("Unsupported type of enum value defined in schema.");
                }
                return convertSimple(ctx.tagName, ctx.id, configuration.asString());
            } else if (ctx.currentSchema.get("$ref").isDefined()) {
                ctx.currentSchema = findRef(ctx.rootSchema, ctx.currentSchema.get("$ref").asString());
                return convert(configuration, ctx);
            } else {
                ModelNode matchingSchema = null;
                if (ctx.currentSchema.hasDefined("oneOf")) {
                    for (ModelNode s : ctx.currentSchema.get("oneOf").asList()) {
                        if (VALIDATOR.validate(configuration, s).isSuccessful()) {
                            if (matchingSchema != null) {
                                matchingSchema = null;
                                break;
                            } else {
                                matchingSchema = s;
                            }
                        }
                    }
                } else if (ctx.currentSchema.hasDefined("anyOf")) {
                    for (ModelNode s : ctx.currentSchema.get("anyOf").asList()) {
                        if (VALIDATOR.validate(configuration, s).isSuccessful()) {
                            matchingSchema = s;
                            break;
                        }
                    }
                } else if (ctx.currentSchema.hasDefined("allOf")) {
                    for (ModelNode s : ctx.currentSchema.get("allOf").asList()) {
                        if (VALIDATOR.validate(configuration, s).isSuccessful()) {
                            matchingSchema = s;
                        } else {
                            matchingSchema = null;
                            break;
                        }
                    }
                }

                if (matchingSchema != null) {
                    ctx.currentSchema = matchingSchema;
                    return convert(configuration, ctx);
                } else {
                    throw new IllegalArgumentException("Could not convert the configuration.");
                }
            }
        }

        if (type.getType() != ModelType.STRING) {
            throw new IllegalArgumentException(
                    "JSON schema allows for multiple possible types. " +
                            "This is not supported by the XML-to-JSON conversion yet.");
        }

        String valueType = type.asString();

        switch (valueType) {
            case "boolean":
                return convertSimple(ctx.tagName, ctx.id, configuration.asString());
            case "integer":
                return convertSimple(ctx.tagName, ctx.id, configuration.asString());
            case "number":
                return convertSimple(ctx.tagName, ctx.id, configuration.asString());
            case "string":
                return convertSimple(ctx.tagName, ctx.id, configuration.asString());
            case "array":
                return convertArray(configuration, ctx);
            case "object":
                return convertObject(configuration, ctx);
            default:
                throw new IllegalArgumentException("Unsupported json value type: " + valueType);
        }
    }

    private static ModelNode findRef(ModelNode rootSchema, String ref) {
        return XmlToJson.JSONPointer.parse(ref).navigate(rootSchema);
    }

    private static PlexusConfiguration convertObject(ModelNode configuration, ConversionContext ctx) {
        XmlPlexusConfiguration object = new XmlPlexusConfiguration(ctx.tagName);
        if (ctx.id != null) {
            object.setAttribute("id", ctx.id);
        }

        ModelNode propertySchemas = ctx.currentSchema.get("properties");
        ModelNode additionalPropSchemas = ctx.currentSchema.get("additionalProperties");
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

            ctx.currentSchema = childSchema;
            ctx.pushTag(key);
            ctx.id = null;

            if (!childSchema.isDefined()) {
                //check if this is an ignorable path
                if (ctx.ignorablePaths.contains(ctx.getCurrentPathString())) {
                    ctx.currentPath.pop();
                    continue;
                }
                throw new IllegalArgumentException("Could not determine the format for the '" + key +
                        "' JSON value during the JSON-to-XML conversion.");
            }

            PlexusConfiguration xmlChild = convert(childConfig, ctx);
            ctx.currentPath.pop();
            object.addChild(xmlChild);
        }
        return object;
    }

    private static PlexusConfiguration convertArray(ModelNode configuration, ConversionContext ctx) {
        ModelNode itemsSchema = ctx.currentSchema.get("items");
        if (!itemsSchema.isDefined()) {
            throw new IllegalArgumentException(
                    "No schema found for items of a list. Cannot continue with XML-to-JSON conversion.");
        }
        PlexusConfiguration list = new XmlPlexusConfiguration(ctx.tagName);
        if (ctx.id != null) {
            list.setAttribute("id", ctx.id);
        }

        for (ModelNode childConfig : configuration.asList()) {
            ctx.tagName = "item";
            ctx.currentSchema = itemsSchema;
            ctx.id = null;
            ctx.currentPath.push("[]");
            PlexusConfiguration child = convert(childConfig, ctx);
            ctx.currentPath.pop();
            list.addChild(child);
        }
        return list;
    }

    private static PlexusConfiguration convertSimple(String tagName, String id, String value) {
        XmlPlexusConfiguration ret = new XmlPlexusConfiguration(tagName);
        if (id != null) {
            ret.setAttribute("id", id);
        }

        ret.setValue(value);
        return ret;
    }

    private static PlexusConfiguration convertOldStyleConfigToXml(Map<String, ModelNode> extensionSchemas,
                                                                  ModelNode jsonConfig) {
        PlexusConfiguration xmlConfig = new XmlPlexusConfiguration("analysisConfiguration");

        extensionCheck: for (Map.Entry<String, ModelNode> e : extensionSchemas.entrySet()) {
            String extensionId = e.getKey();
            ModelNode schema = e.getValue();

            String[] extensionPath = extensionId.split("\\.");

            ModelNode config = jsonConfig;
            for (String segment : extensionPath) {
                if (!config.has(segment)) {
                    continue extensionCheck;
                } else {
                    config = config.get(segment);
                }
            }

            ConversionContext ctx = new ConversionContext();
            ctx.rootSchema = schema;
            ctx.currentSchema = schema;
            ctx.pushTag(extensionId);
            ctx.id = null;
            ctx.ignorablePaths = extensionSchemas.keySet().stream()
                    .filter(id -> !extensionId.equals(id) && id.startsWith(extensionId))
                    .collect(Collectors.toList());
            PlexusConfiguration extXml = convert(config, ctx);
            ctx.currentPath.pop();
            xmlConfig.addChild(extXml);
        }

        return xmlConfig;
    }

    private static PlexusConfiguration
    convertNewStyleConfigToXml(Map<String, ModelNode> extensionSchemas, ModelNode jsonConfig) throws IOException {
        PlexusConfiguration xmlConfig = new XmlPlexusConfiguration("analysisConfiguration");

        for (ModelNode extConfig : jsonConfig.asList()) {
            String extensionId = extConfig.get("extension").asString();
            ModelNode configuration = extConfig.get("configuration");
            String id = extConfig.hasDefined("id") ? extConfig.get("id").asString() : null;

            ModelNode schema = extensionSchemas.get(extensionId);
            if (schema == null) {
                continue;
            }

            ConversionContext ctx = new ConversionContext();
            ctx.rootSchema = schema;
            ctx.currentSchema = schema;
            ctx.pushTag(extensionId);
            ctx.id = id;
            //we don't assign the ignorable paths, because this must not be tracked in a new-style configuration
            PlexusConfiguration extXml = convert(configuration, ctx);
            ctx.currentPath.pop();
            xmlConfig.addChild(extXml);
        }

        return xmlConfig;
    }

    private static final class ConversionContext {
        String id;
        String tagName;
        ModelNode currentSchema;
        ModelNode rootSchema;
        Collection<String> ignorablePaths;
        Deque<String> currentPath = new ArrayDeque<>(4);

        void pushTag(String tag) {
            this.tagName = tag;
            currentPath.push(tag);
        }

        String getCurrentPathString() {
            if (currentPath.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();

            Iterator<String> it = currentPath.descendingIterator();
            if (it.hasNext()) {
                sb.append(it.next());
            }

            while (it.hasNext()) {
                sb.append('.').append(it.next());
            }

            return sb.toString();
        }
    }
}
