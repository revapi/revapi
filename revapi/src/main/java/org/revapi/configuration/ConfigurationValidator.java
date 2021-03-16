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

import java.io.IOException;
import java.io.Reader;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.SpecVersionDetector;
import com.networknt.schema.ValidationMessage;
import org.jboss.dmr.ModelNode;

/**
 * @see #validate(JsonNode, Configurable)
 *
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public final class ConfigurationValidator {

    /**
     * Validates that the full configuration contains valid configuration for given configurable.
     *
     * @param fullConfiguration
     *            the full configuration containing properties for all configurables
     * @param configurable
     *            the configurable to validate the configuration for
     *
     * @return the result of the validation.
     *
     * @throws ConfigurationException
     *             if reading the JSON schemas of the configurable failed
     * 
     * @deprecated use the Jackson-based variant
     */
    @Deprecated
    public ValidationResult validate(@Nonnull ModelNode fullConfiguration, @Nonnull Configurable configurable)
            throws ConfigurationException {
        return validate(JSONUtil.convert(fullConfiguration), configurable);
    }

    /**
     * Validates that the full configuration contains valid configuration for given configurable.
     *
     * @param fullConfiguration
     *            the full configuration containing properties for all configurables
     * @param configurable
     *            the configurable to validate the configuration for
     *
     * @return the result of the validation.
     *
     * @throws ConfigurationException
     *             if reading the JSON schemas of the configurable failed
     */
    public ValidationResult validate(@Nullable JsonNode fullConfiguration, Configurable configurable)
            throws ConfigurationException {
        try {
            if (JSONUtil.isNullOrUndefined(fullConfiguration)) {
                return ValidationResult.success();
            }

            if (!fullConfiguration.isArray()) {
                throw new ConfigurationException("Expecting a JSON array as the configuration object.");
            }

            return _validate(fullConfiguration, configurable);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to validate configuration.", e);
        }
    }

    /**
     * Validates the provided configuration against the provided schema.
     * 
     * @param extensionConfiguration
     *            the actual configuration of some extension (not wrapped in the identifying object as is the case with
     *            the full configuration provided to {@link #validate(ModelNode, Configurable)}.
     * @param configurationSchema
     *            the schema to validate the configuration against
     * 
     * @return the results of the validation
     * 
     * @throws ConfigurationException
     *             if an error occurs during the processing of the data or schema as opposed to a simple validation
     *             failure which would be captured in the returned object
     * 
     * @deprecated use the Jackson-based variant
     */
    @Deprecated
    public ValidationResult validate(@Nonnull ModelNode extensionConfiguration, @Nonnull ModelNode configurationSchema)
            throws ConfigurationException {
        return validate(JSONUtil.convert(extensionConfiguration), JSONUtil.convert(configurationSchema));
    }

    /**
     * Validates the provided configuration against the provided schema.
     * 
     * @param extensionConfiguration
     *            the actual configuration of some extension (not wrapped in the identifying object as is the case with
     *            the full configuration provided to {@link #validate(JsonNode, Configurable)}.
     * @param configurationSchema
     *            the schema to validate the configuration against
     * 
     * @return the results of the validation
     * 
     * @throws ConfigurationException
     *             if an error occurs during the processing of the data or schema as opposed to a simple validation
     *             failure which would be captured in the returned object
     */
    public ValidationResult validate(JsonNode extensionConfiguration, JsonNode configurationSchema)
            throws ConfigurationException {
        try {
            JsonSchema jsonSchema = JsonSchemaFactory.getInstance(detectVersionOrV4(configurationSchema))
                    .getSchema(configurationSchema);
            Set<ValidationMessage> result = jsonSchema.validate(extensionConfiguration);
            return ValidationResult.fromValidationMessages(result);
        } catch (RuntimeException e) {
            throw new ConfigurationException("Failed to validate configuration.", e);
        }
    }

    private ValidationResult _validate(JsonNode fullConfiguration, Configurable configurable) throws IOException {
        String extensionId = configurable.getExtensionId();
        if (extensionId == null) {
            return ValidationResult.success();
        }

        JsonNode schema;
        try (Reader rdr = configurable.getJSONSchema()) {
            if (rdr == null) {
                return ValidationResult.success();
            }
            schema = JSONUtil.parse(rdr);
        }

        JsonNode extensionConfig = null;
        int idx = 0;
        for (JsonNode cfg : fullConfiguration) {
            JsonNode extensionIdNode = cfg.get("extension");
            if (JSONUtil.isNullOrUndefined(extensionIdNode)) {
                throw new ConfigurationException(
                        "Found invalid configuration object without \"extension\" identifier.");
            }

            if (extensionId.equals(extensionIdNode.asText())) {
                extensionConfig = cfg.get("configuration");
                break;
            }
            idx++;
        }

        if (extensionConfig == null) {
            extensionConfig = JsonNodeFactory.instance.nullNode();
        }

        ValidationResult result = validate(extensionConfig, schema);
        if (result.getErrors() != null) {
            for (int i = 0; i < result.getErrors().length; ++i) {
                ValidationResult.Error e = result.getErrors()[i];
                String path = e.dataPath == null ? ""
                        : (e.dataPath.startsWith("$") ? e.dataPath.substring(1) : e.dataPath);

                path = "$[" + idx + "].configuration" + (path.isEmpty() ? "" : ("." + path));

                String message = e.message;
                if (e.dataPath != null && message.startsWith(e.dataPath)) {
                    message = path + message.substring(e.dataPath.length());
                }

                ValidationResult.Error newErr = new ValidationResult.Error(e.code, message, path);
                result.getErrors()[i] = newErr;
            }
        }

        return result;
    }

    private static SpecVersion.VersionFlag detectVersionOrV4(JsonNode schema) {
        try {
            return SpecVersionDetector.detect(schema);
        } catch (JsonSchemaException e) {
            return SpecVersion.VersionFlag.V4;
        }
    }
}
