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

package org.revapi.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Runs the API check of old and new artifacts using the specified configuration of extensions declared as dependencies
 * of the plugin.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class CheckMojo extends AbstractRevapiMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }

        BuildTimeReporter reporter = new BuildTimeReporter(failSeverity.asDifferenceSeverity());

        analyze(reporter);

        if (reporter.hasBreakingProblems()) {
            if (failBuildOnProblemsFound) {
                throw new MojoFailureException(reporter.getAllProblemsMessage());
            } else {
                getLog().info("API problems found but letting the build pass as configured.");
            }
        } else {
            getLog().info("API checks completed without failures.");
        }
    }
}
