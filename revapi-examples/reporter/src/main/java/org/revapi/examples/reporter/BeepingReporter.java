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
package org.revapi.examples.reporter;

import static org.revapi.DifferenceSeverity.EQUIVALENT;

import java.awt.Toolkit;
import java.io.Reader;
import java.io.StringReader;

import org.revapi.AnalysisContext;
import org.revapi.DifferenceSeverity;
import org.revapi.Report;
import org.revapi.Reporter;

/**
 * {@link Reporter}s are used to convey the results of the API analysis to the outside world. They receive the
 * {@link Report}s which contain the list of differences for some element pair. The element pair can have either the
 * elements missing which means that they are absent in either old or new API. If the archive analyzer considers the
 * element somehow corresponding to each other, they become the element pair.
 * <p>
 * Let's try to implement a simple reporter. Our {@code BeepingReporter} will make the computer beep whenever it
 * encounters an element pair with a difference with severity bigger than a configured minimum.
 */
public class BeepingReporter implements Reporter {
    private DifferenceSeverity minSeverity;

    @Override
    public String getExtensionId() {
        return "beep";
    }

    @Override
    public Reader getJSONSchema() {
        // our configuration is going to be a simple string - a camel case name of the severity.
        // i.e. one of equivalent, nonBreaking, potentiallyBreaking or breaking.
        return new StringReader("{\"type\": \"string\"}");
    }

    @Override
    public void initialize(AnalysisContext analysisContext) {
        // find the configured minimum severity
        minSeverity = DifferenceSeverity.fromCamelCase(analysisContext.getConfigurationNode().asText());
    }

    @Override
    public void report(Report report) {
        DifferenceSeverity maxSeverity = report.getDifferences().stream()
                // get all the severities
                .flatMap(d -> d.classification.values().stream())
                // find the maximum severity
                .reduce(EQUIVALENT, (a, b) -> a.compareTo(b) > 0 ? a : b);

        if (maxSeverity.compareTo(this.minSeverity) >= 0) {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    @Override
    public void close() {
    }
}
