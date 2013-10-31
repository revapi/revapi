/*
 * Copyright 2013 Lukas Krejci
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public class TextReporter implements Reporter {

    private boolean reportMatches;
    private int indentationStep;

    @Override
    public void initialize(Map<String, String> properties) {
        String reportMatches = properties.get("TextReporter.reportMatches");
        String indentationStep = properties.get("TextReporter.indentationStep");

        this.reportMatches = reportMatches != null && Boolean.valueOf(reportMatches);
        this.indentationStep = indentationStep == null ? 4 : Integer.parseInt(indentationStep);
    }

    @Override
    public void report(MatchReport matchReport, PrintStream output) {
        switch (matchReport.getMismatchSeverity()) {
        case NONE:
            if (!reportMatches) {
                return;
            } else {
                output.print("MATCH: ");
            }
            break;
        default:
            output.append(matchReport.getCode()).append(": ").append(matchReport.getMismatchSeverity().name())
                .append(": ");
        }

        output.println(matchReport.getOldElement());
        output.print("with: ");
        output.println(matchReport.getNewElement());
        output.println("Description: ");
        printIndented(output, matchReport.getDescription(), 0);
    }

    private void printIndented(PrintStream output, MatchReport.StructuredDescription descr, int indentation) {
        List<String> keys = new ArrayList<>(descr.keySet());
        Collections.sort(keys);

        for (String key : keys) {
            indent(output, indentation);
            output.append(key).append(": ");
            MatchReport.StructuredDescription subDesc = descr.get(key);
            if (subDesc == null) {
                output.println("<no-value>");
            } else if (subDesc.getSimpleText() != null) {
                output.println(subDesc.getSimpleText());
            } else if (subDesc.size() == 0) {
                output.println("<no-value>");
            } else {
                output.println();
                printIndented(output, subDesc, indentation + indentationStep);
            }
        }
    }

    private void indent(PrintStream output, int indentation) {
        for (int i = 0; i < indentation; ++i) {
            output.append(" ");
        }
    }

}
