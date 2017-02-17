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

package org.revapi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.configuration.JSONUtil;

import org.jboss.dmr.ModelNode;

/**
 * An analysis context is an aggregation of the APIs to check and configuration for the analysis.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public final class AnalysisContext {
    public static final class Builder {
        private Locale locale = Locale.getDefault();
        private API oldApi;
        private API newApi;
        private ModelNode configuration;

        public Builder withLocale(Locale locale) {
            this.locale = locale;
            return this;
        }

        public Builder withOldAPI(API api) {
            this.oldApi = api;
            return this;
        }

        public Builder withNewAPI(API api) {
            this.newApi = api;
            return this;
        }

        public Builder withConfiguration(ModelNode data) {
            this.configuration = data;
            return this;
        }

        public Builder withConfigurationFromJSON(String json) {
            this.configuration = ModelNode.fromJSONString(JSONUtil.stripComments(json));
            return this;
        }

        public Builder withConfigurationFromJSONStream(InputStream jsonStream) throws IOException {
            this.configuration = ModelNode
                .fromJSONStream(JSONUtil.stripComments(jsonStream, Charset.forName("UTF-8")));
            return this;
        }

        public Builder mergeConfiguration(ModelNode config) {
            if (configuration == null) {
                configuration = new ModelNode();
            }
            merge(this.configuration, config);
            return this;
        }

        public Builder mergeConfigurationFromJSON(String json) {
            if (configuration == null) {
                configuration = new ModelNode();
            }
            merge(configuration, ModelNode.fromJSONString(JSONUtil.stripComments(json)));
            return this;
        }

        public Builder mergeConfigurationFromJSONStream(InputStream jsonStream) throws IOException {
            if (configuration == null) {
                configuration = new ModelNode();
            }
            InputStream str = JSONUtil.stripComments(jsonStream, Charset.forName("UTF-8"));
            merge(configuration, ModelNode.fromJSONStream(str));
            return this;
        }

        public AnalysisContext build() {
            return new AnalysisContext(locale, configuration, oldApi, newApi);
        }

        public static void merge(ModelNode a, ModelNode b) {
            switch (b.getType()) {
            case LIST:
                for (ModelNode v : b.asList()) {
                    a.add(v.clone());
                }
                break;
            case OBJECT:
                for (String k : b.keys()) {
                    ModelNode ak = a.get(k);
                    merge(ak, b.get(k));
                }
                break;
            default:
                a.set(b);
            }
        }
    }

    private final Locale locale;
    private final ModelNode configuration;
    private final API oldApi;
    private final API newApi;

    /**
     * Constructor
     *
     * @param locale        the locale the analysis reporters should use
     * @param configuration configuration represented as DMR node
     * @param oldApi        the old API
     * @param newApi        the new API
     */
    public AnalysisContext(@Nonnull Locale locale, @Nullable ModelNode configuration, @Nonnull API oldApi,
        @Nonnull API newApi) {
        this.locale = locale;
        this.configuration = configuration == null ? new ModelNode() : configuration;
        this.oldApi = oldApi;
        this.newApi = newApi;
    }

    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    public Builder modified() {
        Builder bld = new Builder();
        bld.configuration = this.configuration;
        bld.locale = this.locale;
        bld.oldApi = this.oldApi;
        bld.newApi = this.newApi;
        return bld;
    }
    
    @Nonnull
    public Locale getLocale() {
        return locale;
    }

    @Nonnull
    public ModelNode getConfiguration() {
        return configuration;
    }

    @Nonnull
    public API getOldApi() {
        return oldApi;
    }

    @Nonnull
    public API getNewApi() {
        return newApi;
    }
}
