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
package org.revapi.reporter.file;

import static org.revapi.ReportComparator.Strategy.HIERARCHICAL;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.revapi.AnalysisContext;
import org.revapi.Criticality;
import org.revapi.DifferenceSeverity;
import org.revapi.Report;
import org.revapi.ReportComparator;
import org.revapi.Reporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class can be used as a base class for reporters that want to write the reports into the files. It provides some
 * basic features like difference filtering based on severity, the ability to specify the file to write to (including
 * "out" and "err" as special file names for standard output and standard error) and whether to overwrite or append to
 * an existing file.
 */
public abstract class AbstractFileReporter implements Reporter {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractFileReporter.class);

    protected DifferenceSeverity minLevel;
    protected Criticality minCriticality;
    protected PrintWriter output;
    protected File file;

    protected boolean shouldClose;
    protected boolean keepEmptyFile;

    protected AnalysisContext analysis;
    private boolean reportsInOutput = false;

    /**
     * For testing.
     *
     * @param wrt
     *            the output writer
     */
    protected void setOutput(PrintWriter wrt) {
        this.output = wrt;
        this.shouldClose = true;
    }

    /**
     * Subclasses should write the reports to the {@link #output} in this method. This method MUST NOT close the output
     * though.
     */
    protected abstract void flushReports() throws IOException;

    @Override
    public void initialize(@Nonnull AnalysisContext analysis) {
        if (this.analysis != null) {
            try {
                flushReports();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to output previous analysis report.");
            }
        }

        this.analysis = analysis;

        String minLevel = analysis.getConfigurationNode().path("minSeverity").asText(null);
        String minCrit = analysis.getConfigurationNode().path("minCriticality").asText(null);
        String output = analysis.getConfigurationNode().path("output").asText(null);
        output = output == null ? "out" : output;

        boolean append = analysis.getConfigurationNode().path("append").asBoolean(false);
        keepEmptyFile = append || analysis.getConfigurationNode().path("keepEmptyFile").asBoolean(true);

        if (minLevel == null && minCrit == null) {
            LOG.warn("At least one of `minLevel` and `minCriticality` should to be defined. Defaulting to"
                    + " the obsolete behavior of reporting all potentially breaking elements.");
            this.minLevel = DifferenceSeverity.POTENTIALLY_BREAKING;
        }

        if (minLevel != null) {
            this.minLevel = DifferenceSeverity.valueOf(minLevel);
        }

        if (minCrit != null) {
            this.minCriticality = analysis.getCriticalityByName(minCrit);
            if (minCriticality == null) {
                throw new IllegalArgumentException("Unknown criticality '" + minCrit + "'.");
            }
        }

        OutputStream out;

        switch (output) {
        case "out":
            out = System.out;
            break;
        case "err":
            out = System.err;
            break;
        default:
            file = new File(output);
            if (file.exists()) {
                if (!file.isFile()) {
                    LOG.warn("The configured file, '" + file.getAbsolutePath() + "' is not a file."
                            + " Defaulting the output to standard output.");
                    out = System.out;
                    break;
                } else if (!file.canWrite()) {
                    LOG.warn("The configured file, '" + file.getAbsolutePath() + "' is not a writable."
                            + " Defaulting the output to standard output.");
                    out = System.out;
                    break;
                }
            } else {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    if (!parent.mkdirs()) {
                        LOG.warn("Failed to create directory structure to write to the configured output file '"
                                + file.getAbsolutePath() + "'. Defaulting the output to standard output.");
                        out = System.out;
                        break;
                    }
                }
            }

            try {
                out = new FileOutputStream(output, append);
            } catch (FileNotFoundException e) {
                LOG.warn("Failed to create the configured output file '" + file.getAbsolutePath() + "'."
                        + " Defaulting the output to standard output.", e);
                out = System.out;
            }
        }

        shouldClose = out != System.out && out != System.err;

        this.output = createOutputWriter(out, analysis);
    }

    /**
     * Creates a print writer to be used as an output from the supplied output stream.
     *
     * This method is called during the default {@link #initialize(AnalysisContext)} and the default implementation
     * creates a print writer writing in UTF-8.
     *
     * @param stream
     *            the stream to convert to a print writer
     * @param ctx
     *            the analysis context which is being used in {@link #initialize(AnalysisContext)}
     * 
     * @return a print writer to be used as output
     */
    protected PrintWriter createOutputWriter(OutputStream stream, AnalysisContext ctx) {
        return new PrintWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
    }

    /**
     * This is the default implementation of the report method that does the initial filtering based on the configured
     * minimum severity and then delegates to {@link #doReport(Report)} if the reporting should really be performed.
     * 
     * @param report
     *            the report with the differences
     */
    @Override
    public void report(@Nonnull Report report) {
        LOG.trace("Received report {}", report);

        if (report.getDifferences().isEmpty()) {
            return;
        }

        if (isReportable(report)) {
            reportsInOutput = true;
            doReport(report);
        }
    }

    protected boolean isReportable(Report report) {
        boolean ret = true;

        // it is guaranteed in initialize() that one of these is not null
        if (minLevel != null) {
            ret = isReportableBySeverity(report);
        }

        if (minCriticality != null) {
            ret = ret && isReportableByCriticality(report);
        }

        return ret;
    }

    private boolean isReportableBySeverity(Report report) {
        return report.getDifferences().stream().flatMap(d -> d.classification.values().stream())
                .anyMatch(s -> s.compareTo(minLevel) >= 0);
    }

    private boolean isReportableByCriticality(Report report) {
        return report.getDifferences().stream().map(d -> d.criticality).filter(Objects::nonNull)
                .anyMatch(c -> c.getLevel() >= minCriticality.getLevel());
    }

    /**
     * The report contains differences of at least the configured severity. This method is called from
     * {@link #report(Report)} by default.
     */
    protected abstract void doReport(Report report);

    @Override
    public void close() throws IOException {
        flushReports();

        if (shouldClose) {
            output.close();
        }

        if (!keepEmptyFile && !reportsInOutput && file != null) {
            if (!file.delete()) {
                LOG.warn("Failed to delete an empty output file: " + file.getAbsolutePath());
            }
        }
    }

    /**
     * @see ReportComparator
     * 
     * @return a comparator that can be used to sort the reports in the order of the compared elements.
     */
    protected Comparator<Report> getReportsByElementOrderComparator() {
        return new ReportComparator.Builder().withComparisonStrategy(HIERARCHICAL).build();
    }
}
