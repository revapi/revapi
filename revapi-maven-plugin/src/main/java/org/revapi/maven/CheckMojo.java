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
package org.revapi.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.revapi.AnalysisResult;

import java.io.StringWriter;
import java.util.stream.Stream;

/**
 * Runs the API check of old and new artifacts using the specified configuration of extensions declared as dependencies
 * of the plugin.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true,
        requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class CheckMojo extends AbstractRevapiMojo {

    /**
     * Whether or not to output the JSON-formatted suggestions for ignoring the found API problems.
     *
     * @since 0.10.4
     */
    @Parameter(property = Props.outputIgnoreSuggestions.NAME, defaultValue = Props.outputIgnoreSuggestions.DEFAULT_VALUE)
    private boolean outputIgnoreSuggestions;

    /**
     * When set to true, all the information about a difference is output in the ignore suggestions. This is useful if
     * you want to modify the ignore suggestion to match some other (broader) set of differences. If you only ever
     * want to suppress concrete differences one-by-one, you can set this to false. If set to false, the ignore
     * suggestions will only contain the minimum information needed to identify the concrete difference.
     *
     * @since 0.10.5
     */
    @Parameter(property = Props.outputNonIdentifyingDifferenceInfo.NAME, defaultValue = Props.outputNonIdentifyingDifferenceInfo.DEFAULT_VALUE)
    private boolean outputNonIdentifyingDifferenceInfo;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }

        StringWriter wrt = new StringWriter();
        BuildTimeReporter reporter;

        String report = null;

        try (AnalysisResult res = analyze(BuildTimeReporter.class,
                BuildTimeReporter.BREAKING_SEVERITY_KEY, failSeverity.asDifferenceSeverity(), "maven-log", getLog(),
                "writer", wrt, BuildTimeReporter.OUTPUT_NON_IDENTIFYING_ATTACHMENTS, outputNonIdentifyingDifferenceInfo)) {

            res.throwIfFailed();

            reporter = res.getExtensions().getFirstExtension(BuildTimeReporter.class, null);

            if (reporter != null && reporter.hasBreakingProblems()) {
                if (failBuildOnProblemsFound) {
                    report = reporter.getAllProblemsMessage();
                    String additionalOutput = wrt.toString();
                    if (!additionalOutput.isEmpty()) {
                        report += "\n\nAdditionally, the configured reporters reported:\n\n" + additionalOutput;
                    }

                    Stream.of(report.split("\n")).forEach(l -> getLog().info(l));

                    if (outputIgnoreSuggestions) {
                        getLog().info("");
                        getLog().info("If you're using the semver-ignore extension, update your module's" +
                                " version to one compatible with the current changes (e.g. mvn package" +
                                " revapi:update-versions). If you want to explicitly ignore this change and provide a" +
                                " justification for it, add the following JSON snippet to your Revapi configuration" +
                                " under \"revapi.ignore\" path:\n\n" + reporter.getIgnoreSuggestion());

                        report += "\nConsult the plugin output above for suggestions on how to ignore the found" +
                                " problems.";
                    }
                } else {
                    getLog().info("API problems found but letting the build pass as configured.");
                }
            } else {
                getLog().info("API checks completed without failures.");
            }
        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to execute the API analysis.", e);
        }

        if (report != null) {
            throw new MojoFailureException(report);
        }
    }
}
