package org.revapi.configuration;

import java.io.Reader;
import java.io.StringReader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.ApiAnalyzer;
import org.revapi.ArchiveAnalyzer;
import org.revapi.CorrespondenceComparatorDeducer;
import org.revapi.DifferenceAnalyzer;
import org.revapi.Element;
import org.revapi.ElementFilter;
import org.revapi.Report;
import org.revapi.Reporter;
import org.revapi.Revapi;

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
    public void testMultipleConfigs() throws Exception {
        String schema = "{" +
                "\"properties\" : {" +
                "   \"id\" : {" +
                "      \"type\" : \"integer\"" +
                "   }," +
                "   \"kachna\" : {" +
                "       \"type\" : \"string\"" +
                "   }" +
                "}}";

        String config = "[" +
                "{\"extension\": \"my-config\", \"configuration\": {\"id\": 3, \"kachna\": \"duck\"}}," +
                "{\"extension\": \"my-config\", \"configuration\": {\"id\": 4, \"kachna\": \"no duck\"}}," +
                "{\"extension\": \"other-config\", \"configuration\": 1}" +
                "]";

        ValidationResult result = test(config, new String[]{"my-config"}, schema);

        Assert.assertTrue(result.toString(), result.isSuccessful());
    }

    @Test
    public void testRevapiValidation() throws Exception {
        String config = "[" +
                "{\"extension\": \"my-config\", \"configuration\": {\"id\": 3, \"kachna\": \"duck\"}}," +
                "{\"extension\": \"my-config\", \"configuration\": {\"id\": 4, \"kachna\": \"no duck\"}}," +
                "{\"extension\": \"other-config\", \"configuration\": 1}" +
                "]";

        AnalysisContext ctx = AnalysisContext.builder().withConfigurationFromJSON(config).build();

        Revapi revapi = Revapi.builder().withFilters(TestFilter.class).withReporters(TestReporter.class)
                .withAnalyzers(DummyApiAnalyzer.class).build();

        ValidationResult res = revapi.validateConfiguration(ctx);

        Assert.assertFalse(res.isSuccessful());
        Assert.assertNotNull(res.getErrors());
        Assert.assertEquals(1, res.getErrors().length);
        Assert.assertEquals("/[2]/configuration", res.getErrors()[0].dataPath);
    }

    public static final class TestFilter implements ElementFilter {
        private static final String SCHEMA = "{" +
                "\"properties\" : {" +
                "   \"id\" : {" +
                "      \"type\" : \"integer\"" +
                "   }," +
                "   \"kachna\" : {" +
                "       \"type\" : \"string\"" +
                "   }" +
                "}}";

        @Nullable @Override public String getExtensionId() {
            return "my-config";
        }

        @Nullable @Override public Reader getJSONSchema() {
            return new StringReader(SCHEMA);
        }

        @Override public void close() throws Exception {
        }

        @Override public void initialize(@Nonnull AnalysisContext analysisContext) {
        }

        @Override public boolean applies(@Nullable Element element) {
            return false;
        }

        @Override public boolean shouldDescendInto(@Nullable Object element) {
            return false;
        }
    }

    public static final class TestReporter implements Reporter {
        @Override public void report(@Nonnull Report report) {
        }

        @Override public void close() throws Exception {
        }

        @Nullable @Override public String getExtensionId() {
            return "other-config";
        }

        @Nullable @Override public Reader getJSONSchema() {
            return new StringReader("{\"type\": \"string\"}");
        }

        @Override public void initialize(@Nonnull AnalysisContext analysisContext) {
        }
    }

    public static final class DummyApiAnalyzer implements ApiAnalyzer {

        @Nonnull @Override public ArchiveAnalyzer getArchiveAnalyzer(@Nonnull API api) {
            return null;
        }

        @Nonnull @Override public DifferenceAnalyzer getDifferenceAnalyzer(@Nonnull ArchiveAnalyzer oldArchive,
                                                                           @Nonnull ArchiveAnalyzer newArchive) {
            return null;
        }

        @Nonnull @Override public CorrespondenceComparatorDeducer getCorrespondenceDeducer() {
            return null;
        }

        @Override public void close() throws Exception {
        }

        @Override public void initialize(@Nonnull AnalysisContext analysisContext) {
        }
    }
}
