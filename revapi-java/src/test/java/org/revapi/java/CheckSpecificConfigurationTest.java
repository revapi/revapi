package org.revapi.java;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.EnumSet;

import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.revapi.AnalysisContext;
import org.revapi.Revapi;
import org.revapi.java.spi.CheckBase;

/**
 * @author Lukas Krejci
 */
public class CheckSpecificConfigurationTest {

    @Test
    public void testNoConfigNoInitialize() throws Exception {
        class FakeCheck extends CheckBase {
            public boolean initializeCalled = false;

            @Override
            public EnumSet<Type> getInterest() {
                return null;
            }

            @Override
            public void initialize(@Nonnull AnalysisContext analysisContext) {
                initializeCalled = true;
            }
        }

        FakeCheck check = new FakeCheck();
        JavaApiAnalyzer analyzer = new JavaApiAnalyzer(Collections.singleton(check));

        analyzer.initialize(AnalysisContext.builder().build().copyWithConfiguration(new ModelNode()));

        Assert.assertFalse(check.initializeCalled);
    }

    @Test
    public void testCheckConfigurationInSchema() throws Exception {
        class FakeCheck extends CheckBase {
            @Override
            public EnumSet<Type> getInterest() {
                return null;
            }

            @Nullable
            @Override
            public String getExtensionId() {
                return "testCheck";
            }

            @Nullable
            @Override
            public Reader getJSONSchema() {
                return new StringReader("{\"type\": \"boolean\"}");
            }
        }

        JavaApiAnalyzer analyzer = new JavaApiAnalyzer(Collections.singleton(new FakeCheck()));

        try (Reader rdr = analyzer.getJSONSchema()) {
            ModelNode schema = ModelNode.fromJSONString(slurp(rdr));
            Assert.assertTrue("boolean".equals(schema.get("properties", "checks", "properties", "testCheck", "type").asString()));
        }
    }

    @Test
    public void testCheckConfigured() throws Exception {
        class FakeCheck extends CheckBase {
            Boolean testCheck = null;
            @Override
            public EnumSet<Type> getInterest() {
                return null;
            }

            @Nullable
            @Override
            public String getExtensionId() {
                return "testCheck";
            }

            @Nullable
            @Override
            public Reader getJSONSchema() {
                return new StringReader("{\"type\": \"boolean\"}");
            }

            @Override
            public void initialize(@Nonnull AnalysisContext analysisContext) {
                testCheck = analysisContext.getConfiguration().asBoolean();
            }
        }

        FakeCheck check = new FakeCheck();
        JavaApiAnalyzer analyzer = new JavaApiAnalyzer(Collections.singleton(check));
        String config = "{\"checks\": {\"testCheck\": true}}";

        analyzer.initialize(AnalysisContext.builder().build().copyWithConfiguration(ModelNode.fromJSONString(config)));

        Assert.assertTrue(check.testCheck);
    }

    @SuppressWarnings("Duplicates")
    private String slurp(Reader rdr) throws IOException {
        char[] buffer = new char[512];
        int cnt;
        StringBuilder bld = new StringBuilder();
        while ((cnt = rdr.read(buffer)) >= 0) {
            bld.append(buffer, 0, cnt);
        }

        return bld.toString();
    }
}
