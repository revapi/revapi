package org.revapi.configuration;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
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

                    SimpleBindings variables = new SimpleBindings();

                    js.eval("var data = " + config + ";", variables);
                    try {
                        js.eval("var schema = " + schema + ";", variables);
                    } catch (ScriptException e) {
                        throw new IllegalArgumentException("Failed to parse the schema: " + schema, e);
                    }

                    variables.put("tv4", js.getContext().getAttribute("tv4", ScriptContext.GLOBAL_SCOPE));

                    String result = (String) js.eval("JSON.stringify(tv4.validateMultiple(data, schema));", variables);

                    PartialValidationResult r = new PartialValidationResult(rootPath, ModelNode.fromJSONString(result));

                    validationResults.add(r);
                }
            }

            return convert(validationResults);
        } catch (IOException | ScriptException e) {
            throw new ConfigurationException("Failed to validate configuration.", e);
        }
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

            SimpleBindings globalScope = new SimpleBindings();
            ctx.setBindings(globalScope, ScriptContext.GLOBAL_SCOPE);

            ret.setContext(ctx);
            initTv4(ret, globalScope);

            jsEngine = new WeakReference<>(ret);
        }

        ret.getContext().setWriter(output);
        ret.getContext().setErrorWriter(output);

        return ret;
    }

    private void initTv4(ScriptEngine engine, Bindings bindings) throws IOException, ScriptException {
        try (Reader rdr = new InputStreamReader(getClass().getResourceAsStream("/tv4.min.js"))) {
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
