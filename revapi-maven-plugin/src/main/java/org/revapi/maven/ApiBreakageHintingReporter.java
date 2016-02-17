/*
 * Copyright $year Lukas Krejci
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
 *
 */
package org.revapi.maven;

import java.io.Reader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.Report;
import org.revapi.Reporter;

/**
 * @author Lukas Krejci
 * @since 0.4.0
 */
class ApiBreakageHintingReporter implements Reporter {

    private ApiChangeLevel changeLevel = ApiChangeLevel.NO_CHANGE;


    @Override
    public void report(@Nonnull Report report) {
        if (changeLevel == ApiChangeLevel.BREAKING_CHANGES) {
            return;
        }

        LOOP:
        for (Difference diff : report.getDifferences()) {
            for (DifferenceSeverity s : diff.classification.values()) {
                boolean breaking = s == DifferenceSeverity.BREAKING;
                if (breaking) {
                    changeLevel = ApiChangeLevel.BREAKING_CHANGES;
                    break LOOP;
                } else {
                    changeLevel = ApiChangeLevel.NON_BREAKING_CHANGES;
                }
            }
        }
    }

    public ApiChangeLevel getChangeLevel() {
        return changeLevel;
    }

    @Override
    public void close() throws Exception {
    }

    @Override
    public @Nullable String[] getConfigurationRootPaths() {
        return null;
    }

    @Override
    public @Nullable Reader getJSONSchema(@Nonnull String configurationRootPath) {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
    }

}
