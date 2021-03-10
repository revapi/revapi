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
package org.revapi.reporter.text;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.revapi.AnalysisContext;
import org.revapi.Report;
import org.revapi.reporter.file.AbstractFileReporter;

/**
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public class TextReporter extends AbstractFileReporter {
    private static final String CONFIG_ROOT_PATH = "revapi.reporter.text";

    private SortedSet<Report> reports;

    private Template template;

    @Override
    protected void setOutput(PrintWriter wrt) {
        super.setOutput(wrt);
    }

    @Nullable
    @Override
    public String getExtensionId() {
        return CONFIG_ROOT_PATH;
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("schema.json"), StandardCharsets.UTF_8);
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysis) {
        super.initialize(analysis);
        String templatePath = analysis.getConfigurationNode().path("template").asText("");
        if (templatePath.isEmpty()) {
            templatePath = null;
        }

        this.reports = new TreeSet<>(getReportsByElementOrderComparator());

        Configuration freeMarker = createFreeMarkerConfiguration();

        template = null;
        try {
            template = templatePath == null ? freeMarker.getTemplate("default-template-with-improbable-name.ftl")
                    : freeMarker.getTemplate(templatePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize the freemarker template.", e);
        }
    }

    /**
     * Creates a new FreeMarker configuration. By default, it is configured as follows:
     * <ul>
     * <li>compatibility level is set to 2.3.23
     * <li>the object wrapper is configured to expose fields
     * <li>API builtins are enabled
     * <li>there are 2 template loaders - 1 for loading templates from /META-INF using a classloader and a second one to
     * load templates from files.
     * </ul>
     * 
     * @return
     */
    protected Configuration createFreeMarkerConfiguration() {
        DefaultObjectWrapperBuilder bld = new DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_23);
        bld.setExposeFields(true);

        Configuration freeMarker = new Configuration(Configuration.VERSION_2_3_23);

        freeMarker.setObjectWrapper(bld.build());
        freeMarker.setAPIBuiltinEnabled(true);
        freeMarker.setTemplateLoader(new MultiTemplateLoader(new TemplateLoader[] {
                new ClassTemplateLoader(getClass(), "/META-INF"), new NaiveFileTemplateLoader() }));

        return freeMarker;
    }

    @Override
    protected void doReport(@Nonnull Report report) {
        reports.add(report);
    }

    protected void flushReports() throws IOException {
        try {
            if (output != null && template != null) {
                HashMap<String, Object> root = new HashMap<>();
                root.put("reports", reports);
                root.put("analysis", analysis);
                template.process(root, output);
            }
        } catch (TemplateException e) {
            throw new IOException("Failed to output the reports.", e);
        }
    }
}
