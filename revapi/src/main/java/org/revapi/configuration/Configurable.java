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

import java.io.Reader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.AnalysisContext;

/**
 * A thing that can be configured from a JSON file.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface Configurable {

    /**
     * Revapi supports a single configuration file for multiple extensions. Each such extension is supposed to define
     * a set of paths in the JSON file that it wants to read the configuration from.
     *
     * @return the root paths in the configuration this configurable understands or null
     */
    @Nullable
    String[] getConfigurationRootPaths();

    /**
     * Returns a reader using which the caller can read the JSON schema for given configuration root path.
     *
     * @param configurationRootPath the root path to get the expected schema for. This will be one of the paths
     *                              returned
     *                              from {@link #getConfigurationRootPaths()}.
     *
     * @return the reader for reading in the schema JSON or null if no schema is needed for given root path.
     */
    @Nullable
    Reader getJSONSchema(@Nonnull String configurationRootPath);

    /**
     * The instance can configure itself for the upcoming analysis from the supplied analysis context.
     *
     * <p>The configuration contained in the context is the FULL configuration of all extensions. I.e. it contains also
     * configuration not intended for this configurable. When reading from the configuration one therefore needs to
     * use the full path to the configuration properties, including the root paths.
     * <p>
     * Note that this method can be called multiple times, each time for a different analysis run.
     *
     * @param analysisContext the context of the upcoming analysis
     */
    void initialize(@Nonnull AnalysisContext analysisContext);
}
