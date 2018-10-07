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
package org.revapi.ant;

import java.io.Reader;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectComponent;
import org.revapi.AnalysisContext;
import org.revapi.CompatibilityType;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.Element;
import org.revapi.Report;
import org.revapi.Reporter;

/**
 * @author Lukas Krejci
 * @since 0.2
 */
public final class AntReporter implements Reporter {
    static final String ANT_REPORTER_LOGGER_KEY = "org.revapi.ant.logger";
    static final String MIN_SEVERITY_KEY = "org.revapi.ant.minSeverity";

    private ProjectComponent logger;
    private DifferenceSeverity minSeverity;

    private boolean errorsReported;

    public boolean isErrorsReported() {
        return errorsReported;
    }

    @Override
    public void report(@Nonnull Report report) {
        Element element = report.getOldElement();
        if (element == null) {
            element = report.getNewElement();
        }

        if (element == null) {
            throw new IllegalStateException("This should not ever happen. Both elements in a report were null.");
        }

        for (Difference difference : report.getDifferences()) {
            DifferenceSeverity maxSeverity = DifferenceSeverity.NON_BREAKING;
            for (Map.Entry<CompatibilityType, DifferenceSeverity> e : difference.classification.entrySet()) {
                if (e.getValue().compareTo(maxSeverity) >= 0) {
                    maxSeverity = e.getValue();
                }
            }

            if (maxSeverity.compareTo(minSeverity) < 0) {
                continue;
            }

            errorsReported = true;

            StringBuilder message = new StringBuilder();

            message.append(element.getFullHumanReadableString()).append(": ").append(difference.code).append(": ")
                .append(difference.description).append(" [");

            for (Map.Entry<CompatibilityType, DifferenceSeverity> e : difference.classification.entrySet()) {
                message.append(e.getKey()).append(": ").append(e.getValue()).append(", ");
            }

            message.replace(message.length() - 2, message.length(), "]");

            logger.log(message.toString(), Project.MSG_ERR);
        }
    }

    @Override
    public void close() throws Exception {
    }

    @Nullable @Override public String getExtensionId() {
        return null;
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        this.logger = (ProjectComponent) analysisContext.getData(ANT_REPORTER_LOGGER_KEY);
        this.minSeverity = (DifferenceSeverity) analysisContext.getData(MIN_SEVERITY_KEY);
        this.errorsReported = false;
    }
}
