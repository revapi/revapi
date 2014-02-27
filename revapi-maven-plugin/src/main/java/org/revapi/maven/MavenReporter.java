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

package org.revapi.maven;

import java.io.IOException;

import javax.annotation.Nonnull;

import org.revapi.ChangeSeverity;
import org.revapi.Configuration;
import org.revapi.Element;
import org.revapi.MatchReport;
import org.revapi.Reporter;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class MavenReporter implements Reporter {

    private final ChangeSeverity breakingSeverity;
    private StringBuilder allProblems;

    public MavenReporter(ChangeSeverity breakingSeverity) {
        this.breakingSeverity = breakingSeverity;
        allProblems = new StringBuilder();
    }

    public boolean hasBreakingProblems() {
        return allProblems.length() > 0;
    }

    public String getAllProblemsMessage() {
        return "The following API problems caused the build to fail:" + allProblems.toString();
    }

    @Override
    public void initialize(@Nonnull Configuration properties) {
    }

    @Override
    public void report(@Nonnull MatchReport matchReport) {
        Element element = matchReport.getOldElement();
        if (element == null) {
            element = matchReport.getNewElement();
        }

        if (element == null) {
            //wat? At least one of old and new should always be non-null
            return;
        }

        for (MatchReport.Problem p : matchReport.getProblems()) {
            StringBuilder message = new StringBuilder(element.getFullHumanReadableString());

            message.append(": ").append(p.code).append(": ").append(p.description);

            ChangeSeverity maxSeverity = ChangeSeverity.NON_BREAKING;
            for (ChangeSeverity s : p.classification.values()) {
                if (maxSeverity.compareTo(s) < 0) {
                    maxSeverity = s;
                }
            }

            if (maxSeverity.compareTo(breakingSeverity) >= 0) {
                allProblems.append("\n").append(element.getFullHumanReadableString()).append(": ").append(p.code)
                    .append(": ").append(p.description);
            }
        }
    }

    @Override
    public void close() throws IOException {
    }
}
