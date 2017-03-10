/*
 * Copyright 2017 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi.maven;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.revapi.AnalysisContext;
import org.revapi.AnalysisResult;
import org.revapi.Revapi;
import org.revapi.configuration.Configurable;
import org.revapi.simple.SimpleReporter;

import com.ximpleware.AutoPilot;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;
import com.ximpleware.XMLModifier;

/**
 * This is a helper goal to convert the old JSON Revapi configuration inside the POM files into the new XML based
 * format. You usually need to run this goal just once in each module.
 *
 * <p>Note that this does not touch the external configuration files. The old and new style configuration still works
 * together well, though.
 *
 * <p>Note that this goal <b>changes the contents of pom.xml</b> of the built modules. You are advised to check
 * the modifications for correctness and to update the formatting of the changed lines to your liking.
 *
 * @author Lukas Krejci
 * @since 0.9.0
 */
@Mojo(name = "convert-config-to-xml", requiresDirectInvocation = true)
public class ConvertToXmlConfigMojo extends AbstractRevapiMojo {

    @Override public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }

        Analyzer analyzer = prepareAnalyzer(project, SimpleReporter.class, Collections.emptyMap());

        Revapi revapi = analyzer.getRevapi();

        AnalysisContext ctx =
                AnalysisContext.builder(revapi).withConfigurationFromJSON(analysisConfiguration.getValue()).build();

        AnalysisResult.Extensions extensions = revapi.prepareAnalysis(ctx);

        List<Configurable> exts = extensions.stream().map(e -> (Configurable) e.getKey()).collect(Collectors.toList());

        try {
            updateAllConfigurations(project, exts);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to convert the JSON configuration in pom.xml to XML format.", e);
        }
    }

    private static PlexusConfiguration convertToXml(Map<String, ModelNode> extensionSchemas, String xmlOrJson)
            throws IOException, XmlPullParserException {
        try {
            ModelNode jsonConfig = ModelNode.fromJSONString(xmlOrJson);

            if (jsonConfig.getType() == ModelType.LIST) {
                return convertNewStyleConfigToXml(extensionSchemas, jsonConfig);
            } else {
                return convertOldStyleConfigToXml(extensionSchemas, jsonConfig);
            }
        } catch (IllegalArgumentException e) {
            //ok, this already is XML
            Xpp3Dom dom = Xpp3DomBuilder.build(new StringReader(xmlOrJson));
            return new XmlPlexusConfiguration(dom);
        }
    }

    private static PlexusConfiguration convertOldStyleConfigToXml(Map<String, ModelNode> extensionSchemas,
                                                                  ModelNode jsonConfig) {
        PlexusConfiguration xmlConfig = new XmlPlexusConfiguration("analysisConfiguration");

        extensionCheck: for (Map.Entry<String, ModelNode> e : extensionSchemas.entrySet()) {
            String extensionId = e.getKey();
            ModelNode schema = e.getValue();

            String[] extensionPath = extensionId.split("\\.");

            ModelNode config = jsonConfig;
            for (String segment : extensionPath) {
                if (!config.has(segment)) {
                    continue extensionCheck;
                } else {
                    config = config.get(segment);
                }
            }

            PlexusConfiguration extXml = SchemaDrivenJSONToXmlConverter.convert(config, schema, extensionId);
            xmlConfig.addChild(extXml);
        }

        return xmlConfig;
    }

    private static PlexusConfiguration
    convertNewStyleConfigToXml(Map<String, ModelNode> extensionSchemas, ModelNode jsonConfig) throws IOException {
        PlexusConfiguration xmlConfig = new XmlPlexusConfiguration("analysisConfiguration");

        for (ModelNode extConfig : jsonConfig.asList()) {
            String extensionId = extConfig.get("extension").asString();
            ModelNode configuration = extConfig.get("configuration");

            ModelNode schema = extensionSchemas.get(extensionId);
            if (schema == null) {
                continue;
            }

            PlexusConfiguration extXml = SchemaDrivenJSONToXmlConverter.convert(configuration, schema, extensionId);
            xmlConfig.addChild(extXml);
        }

        return xmlConfig;
    }



    private static void updateAllConfigurations(MavenProject project, List<Configurable> extensions) throws Exception {
        Map<String, ModelNode> extensionSchemas = new HashMap<>();
        for (Configurable ext : extensions) {
            String extensionId = ext.getExtensionId();
            if (extensionId == null || extensionSchemas.containsKey(extensionId)) {
                continue;
            }

            try (Reader schemaRdr = ext.getJSONSchema()) {
                if (schemaRdr == null) {
                    continue;
                }

                ModelNode schema = ModelNode.fromJSONString(readFull(schemaRdr));

                extensionSchemas.put(extensionId, schema);
            }
        }

        VTDGen gen = new VTDGen();
        gen.enableIgnoredWhiteSpace(true);
        gen.parseFile(project.getFile().getAbsolutePath(), true);

        VTDNav nav = gen.getNav();
        XMLModifier mod = new XMLModifier(nav);

        Callable<Void> update = () -> {
            int textPos = nav.getText();
            String jsonConfig = nav.toRawString(textPos);

            PlexusConfiguration xml = convertToXml(extensionSchemas, jsonConfig);

            mod.insertAfterElement(xml.toString());
            mod.remove();

            return null;
        };

        AutoPilot ap = new AutoPilot(nav);

        ap.selectXPath("//plugin[groupId = 'org.revapi' and artifactId = 'revapi-maven-plugin']/configuration/analysisConfiguration");
        while (ap.evalXPath() != -1) {
            update.call();
        }

        ap.resetXPath();

        ap.selectXPath("//plugin[groupId = 'org.revapi' and artifactId = 'revapi-maven-plugin']/executions/execution/configuration/analysisConfiguration");
        while (ap.evalXPath() != -1) {
            update.call();
        }

        try (OutputStream out = new FileOutputStream(project.getFile())) {
            mod.output(out);
        }
    }

    private static String readFull(Reader rdr) throws IOException {
        char[] buf = new char[512];
        int cnt;

        StringBuilder bld = new StringBuilder();
        while ((cnt = rdr.read(buf)) != -1) {
            bld.append(buf, 0, cnt);
        }

        return bld.toString();
    }
}
