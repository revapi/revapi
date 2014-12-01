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
final class AntReporter implements Reporter {
    private final ProjectComponent logger;
    private final DifferenceSeverity minSeverity;

    AntReporter(ProjectComponent logger, DifferenceSeverity minSeverity) {
        this.logger = logger;
        this.minSeverity = minSeverity;
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

    @Nullable
    @Override
    public String[] getConfigurationRootPaths() {
        return null;
    }

    @Nullable
    @Override
    public Reader getJSONSchema(@Nonnull String configurationRootPath) {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
    }
}
