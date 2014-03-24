/*
 * Copyright 2014 Lukas Krejci
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

package org.revapi;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class TextReporter implements Reporter {
    private static final Logger LOG = LoggerFactory.getLogger(TextReporter.class);
    private static final String CONFIG_ROOT_PATH = "revapi.reporter.text";

    private ChangeSeverity minLevel;
    private PrintWriter output;
    private boolean shouldClose;

    @Nullable
    @Override
    public String[] getConfigurationRootPaths() {
        return new String[]{CONFIG_ROOT_PATH};
    }

    @Nullable
    @Override
    public Reader getJSONSchema(@Nonnull String configurationRootPath) {
        if (CONFIG_ROOT_PATH.equals(configurationRootPath)) {
            return new InputStreamReader(getClass().getResourceAsStream("/META-INF/schema.json"));
        } else {
            return null;
        }
    }

    @Override
    public void initialize(@Nonnull AnalysisContext config) {
        String minLevel = config.getConfiguration().get("revapi", "reporter", "text", "minSeverity").asString();
        String output = config.getConfiguration().get("revapi", "reporter", "text", "output").asString();
        output = output == null ? "out" : output;

        this.minLevel =
            minLevel == null ? ChangeSeverity.POTENTIALLY_BREAKING : ChangeSeverity.valueOf(minLevel);

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
                if (!f.getParentFile().mkdirs()) {
                    LOG.warn("Failed to create directory structure to write to the configured output file '" +
                        f.getAbsolutePath() + "'. Defaulting the output to standard output.");
                    out = System.out;
                } else {
                    try {
                        out = new FileOutputStream(output);
                    } catch (FileNotFoundException e) {
                        LOG.warn("Failed to create the configured output file '" + f.getAbsolutePath() + "'." +
                            " Defaulting the output to standard output.", e);
                        out = System.out;
                    }
                }
            }
        }

        shouldClose = out != System.out && out != System.err;

        this.output = new PrintWriter(out);
    }

    @Override
    public void report(@Nonnull Report report) {
        if (report.getDifferences().isEmpty()) {
            return;
        }

        ChangeSeverity maxReportedSeverity = ChangeSeverity.NON_BREAKING;
        for (Difference d : report.getDifferences()) {
            for (ChangeSeverity c : d.classification.values()) {
                if (c.compareTo(maxReportedSeverity) > 0) {
                    maxReportedSeverity = c;
                }
            }
        }

        if (maxReportedSeverity.compareTo(minLevel) < 0) {
            return;
        }

        Element oldE = report.getOldElement();
        Element newE = report.getNewElement();

        output.print(oldE == null ? "<none>" : oldE.getFullHumanReadableString());
        output.print(" with ");
        output.print(newE == null ? "<none>" : newE.getFullHumanReadableString());
        if (!report.getDifferences().isEmpty()) {
            output.print(": ");
            for (Difference d : report.getDifferences()) {
                output.append(d.name).append(" (").append(d.code).append(")");
                reportClassification(output, d);
                output.append(": ").append(d.description).append("\n");
            }
        }
        output.println();

        output.flush();
    }

    @Override
    public void close() throws IOException {
        if (shouldClose) {
            output.close();
        }
    }

    private void reportClassification(PrintWriter output, Difference difference) {
        Iterator<Map.Entry<CompatibilityType, ChangeSeverity>> it = difference.classification.entrySet().iterator();

        if (it.hasNext()) {
            Map.Entry<CompatibilityType, ChangeSeverity> e = it.next();
            output.append(" ").append(e.getKey().toString()).append(": ").append(e.getValue().toString());
        }

        while (it.hasNext()) {
            Map.Entry<CompatibilityType, ChangeSeverity> e = it.next();
            output.append(", ").append(e.getKey().toString()).append(": ").append(e.getValue().toString());
        }
    }
}
