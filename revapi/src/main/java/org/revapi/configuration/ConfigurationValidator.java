/*
 * Copyright 2015 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.revapi.configuration;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.jboss.dmr.ModelNode;

/**
 * @see #validate(org.jboss.dmr.ModelNode, Configurable)
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public final class ConfigurationValidator {

    private static class PartialValidationResult {
        final String rootPath;
        final ModelNode results;

        private PartialValidationResult(String rootPath, ModelNode results) {
            this.rootPath = rootPath;
            this.results = results;
        }
    }

    private WeakReference<ScriptEngine> jsEngine;

    /**
     * Validates that the full configuration contains valid configuration for given configurable.
     *
     * <p>Note that this handles both old and new types of configuration. If the provided configuration is an object,
     * the old configuration style is used, where each configurable can contain at most 1 configuration specification.
     * If the provided configuration is an array, the new configuration style is used, where the configuration can
     * contain multiple configuration sections for each configurable extension (each such section corresponds to a
     * separately instantiated extension).
     *
     * @param fullConfiguration the full configuration containing properties for all configurables
     * @param configurable      the configurable to validate the configuration for
     *
     * @return the result of the validation.
     *
     * @throws ConfigurationException if reading the JSON schemas of the configurable failed
     */
    public ValidationResult validate(@Nonnull ModelNode fullConfiguration, @Nonnull Configurable configurable)
        throws ConfigurationException {
        try {
            switch (fullConfiguration.getType()) {
                case OBJECT:
                    return validateOldStyle(fullConfiguration, configurable);
                case LIST:
                    return validateNewStyle(fullConfiguration, configurable);
                case UNDEFINED:
                    return ValidationResult.success();
                default:
                    throw new ConfigurationException("Expecting a JSON object or array as the configuration object.");
            }
        } catch (IOException | ScriptException e) {
            throw new ConfigurationException("Failed to validate configuration.", e);
        }
    }

    private ValidationResult validateOldStyle(ModelNode fullConfiguration, Configurable configurable)
            throws IOException, ScriptException {
        String[] rootPaths = configurable.getConfigurationRootPaths();
        if (rootPaths == null || rootPaths.length == 0) {
            return ValidationResult.success();
        }

        StringWriter output = new StringWriter();

        ScriptEngine js = getJsEngine(output);

        List<PartialValidationResult> validationResults = new ArrayList<>();

        for (String rootPath : rootPaths) {
            String[] path = rootPath.split("\\.");
            ModelNode configNode = fullConfiguration.get(path);

            if (!configNode.isDefined()) {
                continue;
            }

            try (Reader schemaReader = configurable.getJSONSchema(rootPath)) {
                if (schemaReader == null) {
                    continue;
                }

                String schema = read(schemaReader);

                StringWriter configJSONWrt = new StringWriter();
                PrintWriter wrt = new PrintWriter(configJSONWrt);
                configNode.writeJSONString(wrt, true);

                String config = configJSONWrt.toString();

                Bindings variables = js.createBindings();

                js.eval("var data = " + config + ";", variables);
                try {
                    js.eval("var schema = " + schema + ";", variables);
                } catch (ScriptException e) {
                    throw new IllegalArgumentException("Failed to parse the schema: " + schema, e);
                }

                variables.put("tv4", js.getContext().getAttribute("tv4", ScriptContext.GLOBAL_SCOPE));

                Object resultObject = js.eval("tv4.validateMultiple(data, schema)", variables);
                ModelNode result = JSONUtil.toModelNode(resultObject);

                PartialValidationResult r = new PartialValidationResult(rootPath, result);

                validationResults.add(r);
            }
        }

        return convert(validationResults);
    }

    private ValidationResult validateNewStyle(ModelNode fullConfiguration, Configurable configurable)
            throws IOException, ScriptException {
        String extensionId = configurable.getExtensionId();
        if (extensionId == null) {
            return ValidationResult.success();
        }

        StringWriter output = new StringWriter();

        ScriptEngine js = getJsEngine(output);

        List<PartialValidationResult> validationResults = new ArrayList<>();

        String schema;
        try (Reader rdr = configurable.getJSONSchema()) {
            if (rdr == null) {
                return ValidationResult.success();
            }
            schema = read(rdr);
        }

        int idx = 0;
        for (ModelNode extensionConfig : fullConfiguration.asList()) {
            ModelNode currentExtensionId = extensionConfig.get("extension");
            if (!currentExtensionId.isDefined()) {
                throw new ConfigurationException(
                        "Found invalid configuration object without \"extension\" identifier.");
            }

            if (!extensionId.equals(currentExtensionId.asString())) {
                continue;
            }

            ModelNode currentConfig = extensionConfig.get("configuration");

            StringWriter configJSONWrt = new StringWriter();
            PrintWriter wrt = new PrintWriter(configJSONWrt);
            currentConfig.writeJSONString(wrt, true);

            String config = configJSONWrt.toString();

            Bindings variables = js.createBindings();

            js.eval("var data = " + config + ";", variables);
            try {
                js.eval("var schema = " + schema + ";", variables);
            } catch (ScriptException e) {
                throw new IllegalArgumentException("Failed to parse the schema: " + schema, e);
            }

            variables.put("tv4", js.getContext().getAttribute("tv4", ScriptContext.GLOBAL_SCOPE));

            Object resultObject = js.eval("tv4.validateMultiple(data, schema)", variables);
            ModelNode result = JSONUtil.toModelNode(resultObject);

            PartialValidationResult r = new PartialValidationResult("[" + idx + "].configuration", result);

            validationResults.add(r);
            idx++;
        }

        return convert(validationResults);
    }

    private ValidationResult convert(List<PartialValidationResult> results) {
        ModelNode result = new ModelNode();

        for (PartialValidationResult r : results) {
            if (r.results.has("errors")) {
                List<ModelNode> errors = r.results.get("errors").asList();
                for (ModelNode error : errors) {
                    if (error.has("dataPath")) {
                        error.get("dataPath")
                            .set("/" + r.rootPath.replace(".", "/") + error.get("dataPath").asString());
                    }
                }
            }

            boolean valid =
                r.results.get("valid").asBoolean() && ((!result.has("valid")) || result.get("valid").asBoolean());

            result.get("valid").set(valid);

            if (result.has("errors")) {
                for (ModelNode e : r.results.get("errors").asList()) {
                    result.get("errors").add(e);
                }
            } else {
                result.get("errors").set(r.results.get("errors"));
            }

            if (result.has("missing")) {
                for (ModelNode m : r.results.get("missing").asList()) {
                    result.get("missing").add(m.asString());
                }
            } else {
                result.get("missing").set(r.results.get("missing"));
            }
        }

        return result.isDefined() ? ValidationResult.fromTv4Results(result) : new ValidationResult(null, null);
    }

    private ScriptEngine getJsEngine(Writer output) throws IOException, ScriptException {
        ScriptEngine ret = null;

        if (jsEngine != null) {
            ret = jsEngine.get();
        }

        if (ret == null) {
            ret = new ScriptEngineManager().getEngineByName("javascript");
            ScriptContext ctx = new SimpleScriptContext();

            Bindings globalScope = ret.createBindings();
            ctx.setBindings(globalScope, ScriptContext.GLOBAL_SCOPE);

            initTv4(ret, globalScope);

            ret.setContext(ctx);

            jsEngine = new WeakReference<>(ret);
        }

        ret.getContext().setWriter(output);
        ret.getContext().setErrorWriter(output);

        return ret;
    }

    private void initTv4(ScriptEngine engine, Bindings bindings) throws IOException, ScriptException {
        try (Reader rdr = new InputStreamReader(getClass().getResourceAsStream("/tv4.min.js"),
                Charset.forName("UTF-8"))) {
            engine.eval(rdr, bindings);
        }
    }

    private static String read(Reader rdr) throws IOException {
        StringBuilder bld = new StringBuilder();
        char[] buffer = new char[4096];

        int cnt;
        while ((cnt = rdr.read(buffer)) != -1) {
            bld.append(buffer, 0, cnt);
        }

        return bld.toString();
    }
}
