/*
 * Copyright 2014-2023 Lukas Krejci
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

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.revapi.AnalysisResult;
import org.revapi.Criticality;
import org.revapi.PipelineConfiguration;

/**
 * Runs the API check of old and new artifacts using the specified configuration of extensions declared as dependencies
 * of the plugin.
 *
 * @author Lukas Krejci
 *
 * @since 0.1
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class CheckMojo extends AbstractRevapiMojo {

    /**
     * Whether or not to output the suggestions for ignoring the found API problems. Before 0.11.5 the suggestions were
     * always JSON formatted. Since 0.11.5 one can choose between JSON and XML using the
     * {@link #ignoreSuggestionsFormat} property.
     * <p>
     * Since 0.11.6 the suggestions are printed even if {@link #failBuildOnProblemsFound} is false. In that case all the
     * problems that have the criticality larger or equal to the {@link #failCriticality} are printed.
     *
     * @since 0.10.4
     */
    @Parameter(property = Props.outputIgnoreSuggestions.NAME, defaultValue = Props.outputIgnoreSuggestions.DEFAULT_VALUE)
    private boolean outputIgnoreSuggestions;

    /**
     * When set to true, all the information about a difference is output in the ignore suggestions. This is useful if
     * you want to modify the ignore suggestion to match some other (broader) set of differences. If you only ever want
     * to suppress concrete differences one-by-one, you can set this to false. If set to false, the ignore suggestions
     * will only contain the minimum information needed to identify the concrete difference.
     *
     * @since 0.10.5
     */
    @Parameter(property = Props.outputNonIdentifyingDifferenceInfo.NAME, defaultValue = Props.outputNonIdentifyingDifferenceInfo.DEFAULT_VALUE)
    private boolean outputNonIdentifyingDifferenceInfo;

    /**
     * The format used to output the ignore suggestions. The default value is "json". The other possible value is "xml"
     * for XML formatted ignore suggestions.
     *
     * @since 0.11.5
     */
    @Parameter(property = Props.ignoreSuggestionsFormat.NAME, defaultValue = Props.ignoreSuggestionsFormat.DEFAULT_VALUE)
    private String ignoreSuggestionsFormat;

    /**
     * If set and if {@link #outputIgnoreSuggestions} is {@code true}, the suggestions are not printed to Maven log but
     * to the specified file.
     *
     * @since 0.11.6
     */
    @Parameter(property = Props.ignoreSuggestionsFile.NAME, defaultValue = Props.ignoreSuggestionsFile.DEFAULT_VALUE)
    private File ignoreSuggestionsFile;

    /**
     * The message that is appended to the end of the plugin error output when the API problems fail the build. This
     * will replace the "Consult the plugin output above for suggestions on how to ignore the found problems." line at
     * the end of the plugin output.
     *
     * @since 0.14.7
     */
    @Parameter(property = Props.buildFailureMessage.NAME, defaultValue = Props.buildFailureMessage.DEFAULT_VALUE)
    private String buildFailureMessage;

    @Override
    public void doExecute() throws MojoExecutionException, MojoFailureException {
        StringWriter wrt = new StringWriter();
        BuildTimeReporter reporter;

        PipelineConfiguration.Builder pipelineCfg = PipelineConfigurationParser.parse(pipelineConfiguration);
        Criticality maxCriticality = determineMaximumCriticality(pipelineCfg.build());

        try (AnalysisResult res = analyze(BuildTimeReporter.class, pipelineCfg,
                BuildTimeReporter.BREAKING_CRITICALITY_KEY, maxCriticality, "maven-log", getLog(), "writer", wrt,
                BuildTimeReporter.OUTPUT_NON_IDENTIFYING_ATTACHMENTS, outputNonIdentifyingDifferenceInfo,
                BuildTimeReporter.SUGGESTIONS_BUILDER_KEY, getSuggestionsBuilder())) {

            res.throwIfFailed();

            reporter = res.getExtensions().getFirstExtension(BuildTimeReporter.class, null);

            if (reporter != null && reporter.hasBreakingProblems()) {
                String report = reporter.getAllProblemsMessage();
                String additionalOutput = wrt.toString();
                if (!additionalOutput.isEmpty()) {
                    report += "\n\nAdditionally, the configured reporters reported:\n\n" + additionalOutput;
                }

                if (outputIgnoreSuggestions || ignoreSuggestionsFile != null) {
                    getLog().info("API problems found.");
                    String message = "If you're using the semver-ignore extension, update your module's"
                            + " version to one compatible with the current changes (e.g. mvn package"
                            + " revapi:update-versions). If you want to explicitly ignore these changes or provide"
                            + " justifications for them, add the " + ignoreSuggestionsFormat + " snippets to your"
                            + " Revapi configuration for the \"revapi.differences\" extension.";
                    String suggestions = reporter.getIgnoreSuggestion();

                    if (outputIgnoreSuggestions) {
                        getLog().info(message + "\n\n" + suggestions);
                    }

                    if (ignoreSuggestionsFile != null && suggestions != null) {
                        Files.write(ignoreSuggestionsFile.toPath(), suggestions.getBytes(StandardCharsets.UTF_8),
                                StandardOpenOption.CREATE);
                        if (!outputIgnoreSuggestions) {
                            getLog().info(message);
                        }
                        getLog().info("Snippets written to " + ignoreSuggestionsFile);
                    }
                    // this will be part of the error message
                    if (failBuildOnProblemsFound) {
                        report += "\n" + buildFailureMessage;
                    }
                }

                if (failBuildOnProblemsFound) {
                    throw new MojoFailureException(report);
                } else if (!outputIgnoreSuggestions) {
                    getLog().info("API problems found but letting the build pass as configured.");
                    Stream.of(report.split("\n")).forEach(l -> getLog().info(l));
                }
            } else {
                getLog().info("API checks completed without failures.");
            }
        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to execute the API analysis.", e);
        }
    }

    private BuildTimeReporter.SuggestionsBuilder getSuggestionsBuilder() {
        switch (ignoreSuggestionsFormat) {
        case "json":
            return new JsonSuggestionsBuilder();
        case "xml":
            return new XmlSuggestionsBuilder();
        default:
            throw new IllegalArgumentException("`ignoreSuggestionsFormat` only accepts \"json\" or \"xml\" but \""
                    + ignoreSuggestionsFormat + "\" was provided.");
        }
    }
}
