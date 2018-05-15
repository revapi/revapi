/*
 * Copyright 2014-2018 Lukas Krejci
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
package org.revapi.java;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.EnumSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;
import org.revapi.AnalysisContext;
import org.revapi.java.spi.CheckBase;

/**
 * @author Lukas Krejci
 */
public class CheckSpecificConfigurationTest {

    @Test
    public void testEmptyConfigWhenNoExtensionId() throws Exception {
        class FakeCheck extends CheckBase {
            public boolean emptyConfig = false;

            @Override
            public EnumSet<Type> getInterest() {
                return null;
            }

            @Override
            public void initialize(@Nonnull AnalysisContext analysisContext) {
                emptyConfig = !analysisContext.getConfiguration().isDefined();
            }
        }

        FakeCheck check = new FakeCheck();
        JavaApiAnalyzer analyzer = new JavaApiAnalyzer(Collections.singleton(check));

        analyzer.initialize(AnalysisContext.builder().build().copyWithConfiguration(ModelNode.fromJSONString("{}")));

        Assert.assertTrue(check.emptyConfig);
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
