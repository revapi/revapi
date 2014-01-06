/*
 * Copyright 2014 Lukas Krejci
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

package org.revapi;

import java.io.PrintStream;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public class TextReporter implements Reporter {

    private boolean reportMatches;
    private int indentationStep;

    @Override
    public void initialize(Configuration properties) {
        String reportMatches = properties.get("TextReporter.reportMatches");
        String indentationStep = properties.get("TextReporter.indentationStep");

        this.reportMatches = reportMatches != null && Boolean.valueOf(reportMatches);
        this.indentationStep = indentationStep == null ? 4 : Integer.parseInt(indentationStep);
    }

    @Override
    public void report(MatchReport matchReport, PrintStream output) {
        if (!reportMatches && matchReport.getProblems().isEmpty()) {
            return;
        }

        if (matchReport.getProblems().isEmpty()) {
            output.print("MATCH: ");
        }

        output.print(matchReport.getOldElement());
        output.print(" with ");
        output.println(matchReport.getNewElement());
        if (!matchReport.getProblems().isEmpty()) {
            output.append(":");
            for (MatchReport.Problem p : matchReport.getProblems()) {
                output.append(p.code).append(p.severity.name()).append(" (").append(p.compatibility.name())
                    .append("): ").append(p.description).append("\n");
            }
        }
    }
}
