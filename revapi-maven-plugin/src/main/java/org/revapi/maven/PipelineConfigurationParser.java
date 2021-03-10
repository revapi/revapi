/*
 * Copyright 2014-2021 Lukas Krejci
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

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.revapi.Criticality;
import org.revapi.DifferenceSeverity;
import org.revapi.PipelineConfiguration;
import org.revapi.configuration.JSONUtil;

final class PipelineConfigurationParser {

    private PipelineConfigurationParser() {

    }

    static PipelineConfiguration.Builder parse(PlexusConfiguration pipelineConfiguration) {
        String jsonConfig = pipelineConfiguration == null ? null : pipelineConfiguration.getValue();

        PipelineConfiguration.Builder ret;

        if (jsonConfig == null) {
            // we're seeing XML. PipelineConfiguration is a set "format", not something dynamic as the extension
            // configurations. We can therefore try to parse it straight away.
            ret = parsePipelineConfigurationXML(pipelineConfiguration);
        } else {
            JsonNode json = JSONUtil.parse(jsonConfig);
            ret = PipelineConfiguration.parse(json);
        }

        // important to NOT add any extensions here yet. That's the job of the pipelineModifier that is responsible
        // to construct
        return ret;

    }

    private static PipelineConfiguration.Builder parsePipelineConfigurationXML(
            PlexusConfiguration pipelineConfiguration) {
        PipelineConfiguration.Builder bld = PipelineConfiguration.builder();

        if (pipelineConfiguration == null) {
            return bld;
        }

        for (PlexusConfiguration c : pipelineConfiguration.getChildren()) {
            switch (c.getName()) {
            case "analyzers":
                parseIncludeExclude(c, bld::addAnalyzerExtensionIdInclude, bld::addAnalyzerExtensionIdExclude);
                break;
            case "reporters":
                parseIncludeExclude(c, bld::addReporterExtensionIdInclude, bld::addReporterExtensionIdExclude);
                break;
            case "filters":
                parseIncludeExclude(c, bld::addFilterExtensionIdInclude, bld::addFilterExtensionIdExclude);
                break;
            case "transforms":
                parseIncludeExclude(c, bld::addTransformExtensionIdInclude, bld::addTransformExtensionIdExclude);
                break;
            case "transformBlocks":
                for (PlexusConfiguration b : c.getChildren()) {
                    List<String> blockIds = Stream.of(b.getChildren()).map(PlexusConfiguration::getValue)
                            .collect(toList());
                    bld.addTransformationBlock(blockIds);
                }
                break;
            case "criticalities":
                for (PlexusConfiguration t : c.getChildren()) {
                    String name = t.getChild("name").getValue();
                    int level = Integer.parseInt(t.getChild("level").getValue());
                    bld.addCriticality(new Criticality(name, level));
                }
                break;
            case "severityMapping":
                for (PlexusConfiguration m : c.getChildren()) {
                    String severityName = m.getName();
                    String criticalityName = m.getValue();
                    DifferenceSeverity severity = DifferenceSeverity.fromCamelCase(severityName);
                    if (severity == null) {
                        throw new IllegalArgumentException("Unknown severity encountered while processing the"
                                + " severityMapping: " + severityName);
                    }

                    bld.addUntypedSeverityMapping(severity, criticalityName);
                }
            }
        }

        return bld;
    }

    private static void parseIncludeExclude(PlexusConfiguration parent, Consumer<String> handleInclude,
            Consumer<String> handleExclude) {

        PlexusConfiguration include = parent.getChild("include");
        PlexusConfiguration exclude = parent.getChild("exclude");

        if (include != null) {
            Stream.of(include.getChildren()).forEach(c -> handleInclude.accept(c.getValue()));
        }

        if (exclude != null) {
            Stream.of(exclude.getChildren()).forEach(c -> handleExclude.accept(c.getValue()));
        }
    }
}
