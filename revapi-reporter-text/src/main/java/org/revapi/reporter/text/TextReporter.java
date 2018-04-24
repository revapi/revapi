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
package org.revapi.reporter.text;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.Element;
import org.revapi.Report;
import org.revapi.Reporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class TextReporter implements Reporter {
    private static final Logger LOG = LoggerFactory.getLogger(TextReporter.class);
    private static final String CONFIG_ROOT_PATH = "revapi.reporter.text";

    private DifferenceSeverity minLevel;
    private PrintWriter output;
    private boolean shouldClose;

    private SortedSet<Report> reports;

    private Template template;

    private AnalysisContext analysis;

    @Nullable
    @Override
    public String getExtensionId() {
        return CONFIG_ROOT_PATH;
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("/META-INF/schema.json"),
                Charset.forName("UTF-8"));
    }

    /**
     * For testing.
     *
     * @param wrt the output writer
     */
    void setOutput(PrintWriter wrt) {
        this.output = wrt;
        this.shouldClose = true;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysis) {
        //noinspection ConstantConditions
        if (analysis != null) {
            try {
                flushReports();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to output previous analysis report.");
            }
        }

        this.analysis = analysis;

        String minLevel = analysis.getConfiguration().get("minSeverity").asString();
        String output = analysis.getConfiguration().get("output").asString();
        output = "undefined".equals(output) ? "out" : output;

        String templatePath = analysis.getConfiguration().get("template").asString();
        if ("undefined".equals(templatePath)) {
            templatePath = null;
        }

        boolean append = analysis.getConfiguration().get("append").asBoolean(false);

        this.minLevel = "undefined".equals(minLevel) ? DifferenceSeverity.POTENTIALLY_BREAKING :
                DifferenceSeverity.valueOf(minLevel);

        OutputStream out;

        switch (output) {
        case "out":
            out = System.out;
            break;
        case "err":
            out = System.err;
            break;
        default:
            File f = new File(output);
            if (f.exists() && !f.canWrite()) {
                LOG.warn(
                        "The configured file for text reporter, '" + f.getAbsolutePath() + "' is not a writable file." +
                                " Defaulting the output to standard output.");
                out = System.out;
            } else {
                File parent = f.getParentFile();
                if (!parent.exists()) {
                    if (!parent.mkdirs()) {
                        LOG.warn("Failed to create directory structure to write to the configured output file '" +
                                f.getAbsolutePath() + "'. Defaulting the output to standard output.");
                        out = System.out;
                        break;
                    }
                }

                try {
                    out = new FileOutputStream(output, append);
                } catch (FileNotFoundException e) {
                    LOG.warn("Failed to create the configured output file '" + f.getAbsolutePath() + "'." +
                            " Defaulting the output to standard output.", e);
                    out = System.out;
                }
            }
        }

        shouldClose = out != System.out && out != System.err;

        this.output = new PrintWriter(new OutputStreamWriter(out, Charset.forName("UTF-8")));

        this.reports = new TreeSet<>((r1, r2) -> {
            Element r1El = r1.getOldElement() == null ? r1.getNewElement() : r1.getOldElement();
            Element r2El = r2.getOldElement() == null ? r2.getNewElement() : r2.getOldElement();

            Deque<Element> r1Ancestry = new ArrayDeque<>();
            Deque<Element> r2Ancestry = new ArrayDeque<>();

            while (r1El != null) {
                r1Ancestry.push(r1El);
                r1El = r1El.getParent();
            }

            while (r2El != null) {
                r2Ancestry.push(r2El);
                r2El = r2El.getParent();
            }

            while (!r1Ancestry.isEmpty() && !r2Ancestry.isEmpty()) {
                int order = r1Ancestry.pop().compareTo(r2Ancestry.pop());
                if (order != 0) {
                    return order;
                }
            }

            return r1Ancestry.size() - r2Ancestry.size();
        });

        Configuration freeMarker = createFreeMarkerConfiguration();

        template = null;
        try {
            template = templatePath == null
                    ? freeMarker.getTemplate("default-template-with-improbable-name@@#(*&$)(.ftl")
                    : freeMarker.getTemplate(templatePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize the freemarker template.", e);
        }
    }

    /**
     * Creates a new FreeMarker configuration.
     * By default, it is configured as follows:
     * <ul>
     * <li>compatibility level is set to 2.3.23
     * <li>the object wrapper is configured to expose fields
     * <li>API builtins are enabled
     * <li>there are 2 template loaders - 1 for loading templates from /META-INF using a classloader and a second
     *     one to load templates from files.
     * </ul>
     * @return
     */
    protected Configuration createFreeMarkerConfiguration() {
        DefaultObjectWrapperBuilder bld = new DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_23);
        bld.setExposeFields(true);

        Configuration freeMarker = new Configuration(Configuration.VERSION_2_3_23);

        freeMarker.setObjectWrapper(bld.build());
        freeMarker.setAPIBuiltinEnabled(true);
        freeMarker.setTemplateLoader(new MultiTemplateLoader(
                new TemplateLoader[]{new ClassTemplateLoader(getClass(), "/META-INF"),
                        new NaiveFileTemplateLoader()}));

        return freeMarker;
    }

    @Override
    public void report(@Nonnull Report report) {
        LOG.trace("Received report {}", report);

        if (report.getDifferences().isEmpty()) {
            return;
        }

        DifferenceSeverity maxReportedSeverity = DifferenceSeverity.NON_BREAKING;
        for (Difference d : report.getDifferences()) {
            for (DifferenceSeverity c : d.classification.values()) {
                if (c.compareTo(maxReportedSeverity) > 0) {
                    maxReportedSeverity = c;
                }
            }
        }

        if (maxReportedSeverity.compareTo(minLevel) < 0) {
            return;
        }

        reports.add(report);
    }

    @Override
    public void close() throws IOException {
        flushReports();

        if (shouldClose) {
            output.close();
        }
    }

    private void flushReports() throws IOException {
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
