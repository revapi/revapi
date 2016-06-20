/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.revapi.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.revapi.simple.SimpleReporter;

/**
 * @author Lukas Krejci
 * @since 0.4.0
 */
@Mojo(name = "validate-configuration", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class ValidateConfigurationMojo extends AbstractRevapiMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try (Analyzer analyzer = prepareAnalyzer(new SimpleReporter())) {
            if (analyzer != null) {
                analyzer.validateConfiguration();
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to validate configuration.", e);
        }
    }
}
