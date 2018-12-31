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
     * The identifier of this configurable extension in the configuration file. This should be globally unique, but
     * human readable, so a package name or something similar would be a good candidate. Core revapi extensions have
     * the extension ids always starting with "revapi.".
     *
     * @return the unique identifier of this configurable extension
     */
    String getExtensionId();

    /**
     * This method must not return null if {@link #getExtensionId()} returns a non-null value.
     *
     * @return a json schema to validate the configuration of this configurable against
     */
    @Nullable Reader getJSONSchema();

    /**
     * The instance can configure itself for the upcoming analysis from the supplied analysis context.
     *
     * <p>The configuration contained in the supplied analysis context is solely the one provided for this configurable
     * instance and conforms to its schema.
     *
     * Note that this method can be called multiple times, each time for a different analysis run.
     *
     * @param analysisContext the context of the upcoming analysis
     */
    void initialize(@Nonnull AnalysisContext analysisContext);
}
