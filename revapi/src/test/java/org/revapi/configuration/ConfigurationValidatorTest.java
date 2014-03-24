package org.revapi.configuration;

import java.io.Reader;
import java.io.StringReader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.junit.Assert;
import org.junit.Test;

import org.jboss.dmr.ModelNode;
import org.revapi.AnalysisContext;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class ConfigurationValidatorTest {

    private ValidationResult test(String object, final String[] rootPaths, final String... schemas) {
        ConfigurationValidator validator = new ConfigurationValidator();

        ModelNode fullConfig = ModelNode.fromJSONString(object);

        Configurable fakeConfigurable = new Configurable() {
            @Nullable
            @Override
            public String[] getConfigurationRootPaths() {
                return rootPaths;
            }

            @Nullable
            @Override
            public Reader getJSONSchema(@Nonnull String configurationRootPath) {
                int i = 0;
                for (String r : rootPaths) {
                    if (r.equals(configurationRootPath)) {
                        return new StringReader(schemas[i]);
                    }
                    ++i;
                }
                return null;
            }

            @Override
            public void initialize(@Nonnull AnalysisContext analysisContext) {
            }
        };

        return validator.validate(fullConfig, fakeConfigurable);
    }

    @Test
    public void testValidSchema() throws Exception {
        final String schema = "{" +
            "\"properties\" : {" +
            "   \"id\" : {" +
            "      \"type\" : \"integer\"" +
            "   }" +
            "}}";

        String object = "{\"my-config\" : {\"id\" : 3}}";

        ValidationResult result = test(object, new String[]{"my-config"}, schema);

        Assert.assertTrue(result.toString(), result.isSuccessful());
    }

    @Test
    public void testSingleFailure() throws Exception {
        final String schema = "{" +
            "\"properties\" : {" +
            "   \"id\" : {" +
            "      \"type\" : \"integer\"" +
            "   }" +
            "}}";

        String object = "{\"my-config\" : {\"id\" : \"3\"}}";

        ValidationResult result = test(object, new String[]{"my-config"}, schema);

        Assert.assertFalse(result.toString(), result.isSuccessful());
        Assert.assertEquals(1, result.getErrors().length);
    }

    @Test
    public void testMultipleFailures() throws Exception {
        final String schema = "{" +
            "\"properties\" : {" +
            "   \"id\" : {" +
            "      \"type\" : \"integer\"" +
            "   }," +
            "   \"kachna\" : {" +
            "       \"type\" : \"string\"" +
            "   }" +
            "}}";

        String object = "{\"my-config\" : {\"id\" : \"3\", \"kachna\" : 42}}";

        ValidationResult result = test(object, new String[]{"my-config"}, schema);

        Assert.assertFalse(result.toString(), result.isSuccessful());
        Assert.assertEquals(2, result.getErrors().length);
    }

    @Test
    public void testMultiSchemaConfiguration() throws Exception {

    }

    @Test
    public void testMultipleEvaluations() throws Exception {

    }
}
