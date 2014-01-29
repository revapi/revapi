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

import org.apache.maven.plugin.logging.Log;

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

    private final Log log;

    public MavenReporter(Log log) {
        this.log = log;
    }

    @Override
    public void initialize(Configuration properties) {
    }

    @Override
    public void report(MatchReport matchReport) {
        Element element = matchReport.getOldElement();
        if (element == null) {
            element = matchReport.getNewElement();
        }

        for (MatchReport.Problem p : matchReport.getProblems()) {
            StringBuilder message = new StringBuilder(element.toString());

            message.append(": ").append(p.name).append(" (").append(p.code).append(")");

            ChangeSeverity maxSeverity = ChangeSeverity.NON_BREAKING;
            for (ChangeSeverity s : p.classification.values()) {
                if (maxSeverity.compareTo(s) > 0) {
                    maxSeverity = s;
                }
            }

            switch (maxSeverity) {
            case NON_BREAKING:
                log.info(message);
                break;
            case POTENTIALLY_BREAKING:
                log.warn(message);
                break;
            case BREAKING:
                log.error(message);
                break;
            }
        }
    }
}
