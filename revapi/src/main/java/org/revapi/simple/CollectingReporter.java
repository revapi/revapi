/*
 * Copyright 2014-2020 Lukas Krejci
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
package org.revapi.simple;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.revapi.Report;
import org.revapi.Reporter;

/**
 * This is a simple helper class that can be used by the users to collect all the
 * reports from Revapi.
 */
public abstract class CollectingReporter extends SimpleConfigurable implements Reporter {

    private final List<Report> reports = new ArrayList<>();

    @Override
    public void report(@Nonnull Report report) {
        reports.add(report);
    }

    public List<Report> getReports() {
        return reports;
    }
}
