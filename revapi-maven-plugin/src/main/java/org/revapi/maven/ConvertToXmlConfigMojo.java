/*
 * Copyright 2014-2020 Lukas Krejci
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jboss.dmr.ModelNode;
import org.revapi.AnalysisContext;
import org.revapi.AnalysisResult;
import org.revapi.Revapi;
import org.revapi.configuration.Configurable;
import org.revapi.configuration.JSONUtil;
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
@Mojo(name = "convert-config-to-xml", requiresDirectInvocation = true, defaultPhase = LifecyclePhase.VALIDATE)
public class ConvertToXmlConfigMojo extends AbstractRevapiMojo {

    /**
     * Whether to convert the {@code analysisConfiguration} elements in pom.xml from JSON to XML or not.
     */
    @Parameter(property = Props.convertPomXml.NAME, defaultValue = Props.convertPomXml.DEFAULT_VALUE)
    private boolean convertPomXml;

    /**
     * Whether to convert the contents of the external configuration files specified by the
     * {@code analysisConfigurationFiles} from JSON to XML.
     *
     * <p>Note that external configuration files with custom root elements are not supported, because it would not be
     * clear how to convert the rest of the file into XML.
     *
     * <p>Also note that the original file will be left intact by the conversion and a new file with the same name and
     * ".xml" extension will be created in the same directory and the pom.xml will be updated to point to this new file.
     * You should delete the old file after making sure the conversion went fine.
     */
    @Parameter(property = Props.convertAnalysisConfigurationFiles.NAME,
            defaultValue = Props.convertAnalysisConfigurationFiles.DEFAULT_VALUE)
    private boolean convertAnalysisConfigurationFiles;

    @Override public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }

        AnalyzerBuilder.Result res = buildAnalyzer(project, PipelineConfigurationParser.parse(pipelineConfiguration),
                SilentReporter.class, Collections.emptyMap());
        if (res.skip) {
            return;
        }

        Revapi revapi = res.analyzer.getRevapi();

        AnalysisContext ctx = AnalysisContext.builder(revapi).build();

        AnalysisResult.Extensions extensions = revapi.prepareAnalysis(ctx);

        Map<String, ModelNode> knownExtensionSchemas;
        try {
            knownExtensionSchemas = getKnownExtensionSchemas(extensions);
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failed to extract the extension schemas from the configured Revapi extensions.", e);
        }

        int indentationSize;
        try (BufferedReader rdr = new BufferedReader(new FileReader(project.getFile()))) {
            indentationSize = XmlUtil.estimateIndentationSize(rdr);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read pom.xml", e);
        }

        if (convertPomXml) {
            try {
                updateAllConfigurations(project.getFile(), knownExtensionSchemas, indentationSize);
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to convert the JSON configuration in pom.xml to XML format.",
                        e);
            }
        }

        if (convertAnalysisConfigurationFiles) {
            try {
                updateAllConfigurationFiles(project, knownExtensionSchemas, indentationSize);
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to update the configuration files.", e);
            }
        }
    }

    private void updateAllConfigurationFiles(MavenProject project, Map<String, ModelNode> extensionSchemas,
                                             int indentationSize) throws Exception {
        VTDGen gen = new VTDGen();
        gen.enableIgnoredWhiteSpace(true);
        gen.parseFile(project.getFile().getAbsolutePath(), true);

        VTDNav nav = gen.getNav();
        XMLModifier mod = new XMLModifier(nav);

        AutoPilot ap = new AutoPilot(nav);

        ThrowingConsumer<String> update = xpath -> {
            ap.resetXPath();
            ap.selectXPath(xpath);

            while (ap.evalXPath() != -1) {
                int textPos = nav.getText();

                String configFile = nav.toString(textPos);

                File newFile = updateConfigurationFile(new File(configFile), extensionSchemas, indentationSize);
                if (newFile == null) {
                    continue;
                }

                mod.updateToken(textPos, newFile.getPath());
            }
        };

        update.accept("//plugin[groupId = 'org.revapi' and artifactId = 'revapi-maven-plugin']" +
                "/configuration/analysisConfigurationFiles/*[not(self::configurationFile)]");
        update.accept("//plugin[groupId = 'org.revapi' and artifactId = 'revapi-maven-plugin']" +
                "/configuration/analysisConfigurationFiles/configurationFile[not(roots)]/path");

        update.accept("//plugin[groupId = 'org.revapi' and artifactId = 'revapi-maven-plugin']" +
                "/executions/execution/configuration/analysisConfigurationFiles/*[not(self::configurationFile)]");
        update.accept("//plugin[groupId = 'org.revapi' and artifactId = 'revapi-maven-plugin']" +
                "/executions/execution/configuration/analysisConfigurationFiles/configurationFile[not(roots)]/path");

        try (OutputStream out = new FileOutputStream(project.getFile())) {
            mod.output(out);
        }
    }

    private File updateConfigurationFile(File configFile, Map<String, ModelNode> extensionSchemas, int indentationSize)
            throws Exception {

        ModelNode jsonConfig;
        try (InputStream is = new FileInputStream(configFile)) {
            jsonConfig = ModelNode.fromJSONStream(is);
        } catch (IllegalArgumentException e) {
            //k, probably XML already
            return null;
        }

        PlexusConfiguration xml = SchemaDrivenJSONToXmlConverter.convertToXml(extensionSchemas, jsonConfig);

        File newFile = configFile;

        String fileExtension = getFileExtension(newFile);
        if (fileExtension != null && fileExtension.equalsIgnoreCase("json")) {
            String newFilePath = newFile.getPath().substring(0, newFile.getPath().length() - fileExtension.length())
                    + "xml";
            newFile = new File(newFilePath);
        }

        try (Writer wrt = new FileWriter(newFile)) {
            StringWriter pretty = new StringWriter();
            XmlUtil.toIndentedString(xml, indentationSize, 0, pretty);
            wrt.write(pretty.toString());
        }

        return newFile;
    }

    private static PlexusConfiguration convertToXml(Map<String, ModelNode> extensionSchemas, String xmlOrJson)
            throws IOException, XmlPullParserException {
        ModelNode jsonConfig;
        try {
            jsonConfig = ModelNode.fromJSONString(JSONUtil.stripComments(xmlOrJson));
        } catch (IllegalArgumentException e) {
            //ok, this already is XML
            return null;
        }
        return SchemaDrivenJSONToXmlConverter.convertToXml(extensionSchemas, jsonConfig);
    }

    private static void updateAllConfigurations(File pomXml, Map<String, ModelNode> extensionSchemas,
                                                int indentationSize) throws Exception {
        VTDGen gen = new VTDGen();
        gen.enableIgnoredWhiteSpace(true);
        gen.parseFile(pomXml.getAbsolutePath(), true);

        VTDNav nav = gen.getNav();
        XMLModifier mod = new XMLModifier(nav);

        Callable<Void> update = () -> {
            int textPos = nav.getText();
            String jsonConfig = nav.toRawString(textPos);

            PlexusConfiguration xml = convertToXml(extensionSchemas, jsonConfig);
            if (xml == null) {
                return null;
            }

            StringWriter pretty = new StringWriter();
            XmlUtil.toIndentedString(xml, indentationSize, nav.getTokenDepth(textPos), pretty);

            //remove the first indentation, because text is already indented
            String prettyXml = pretty.toString().substring(indentationSize * nav.getTokenDepth(textPos));

            mod.insertAfterElement(prettyXml);
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

        try (OutputStream out = new FileOutputStream(pomXml)) {
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

    private static Map<String, ModelNode> getKnownExtensionSchemas(AnalysisResult.Extensions extensions)
            throws IOException {
        List<Configurable> exts = extensions.stream().map(e -> (Configurable) e.getKey().getInstance())
                .collect(Collectors.toList());

        Map<String, ModelNode> extensionSchemas = new HashMap<>();
        for (Configurable ext : exts) {
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

        return extensionSchemas;
    }

    private static String getFileExtension(File f) {
        String extension = null;

        String path = f.getPath();

        int i = path.lastIndexOf('.');
        int p = path.lastIndexOf(File.separator);

        if (i > p) {
            extension = path.substring(i + 1);
        }

        return extension;
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }

    public static final class SilentReporter extends SimpleReporter {

        @Override
        public String getExtensionId() {
            return "revapi.maven.internal.silentReporter";
        }
    }
}
