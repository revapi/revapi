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
package org.revapi.reporter.json;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.json.spi.JsonProvider;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;

import org.revapi.AnalysisContext;
import org.revapi.CompatibilityType;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.Report;
import org.revapi.reporter.file.AbstractFileReporter;

public class JsonReporter extends AbstractFileReporter {
    private Set<Report> reports;
    private JsonGeneratorFactory jsonFactory;

    @Override
    protected void setOutput(PrintWriter wrt) {
        super.setOutput(wrt);
    }

    @Override
    public String getExtensionId() {
        return "revapi.reporter.json";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("schema.json"), StandardCharsets.UTF_8);
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        super.initialize(analysisContext);
        boolean prettyPrint = analysisContext.getConfiguration().get("indent").asBoolean(false);

        Map<String, Object> config = prettyPrint
                ? singletonMap(JsonGenerator.PRETTY_PRINTING, null)
                : emptyMap();

        jsonFactory = JsonProvider.provider().createGeneratorFactory(config);

        this.reports = new TreeSet<>(getReportsByElementOrderComparator());
    }

    @Override
    protected void flushReports() {
        JsonGenerator gen = jsonFactory.createGenerator(output);
        gen.writeStartArray();
        reports.stream().flatMap(r -> {
            String oldEl = r.getOldElement() == null ? null : r.getOldElement().getFullHumanReadableString();
            String newEl = r.getNewElement() == null ? null : r.getNewElement().getFullHumanReadableString();

            return r.getDifferences().stream().map(d -> new DifferenceWithElements(oldEl, newEl, d));
        }).forEach(d -> writeDifference(gen, d));
        gen.writeEnd();
        gen.flush();
    }

    @Override
    protected void doReport(Report report) {
        reports.add(report);
    }

    private static void writeDifference(JsonGenerator gen, DifferenceWithElements de) {
        gen.writeStartObject();
        Difference d = de.diff;
        write(gen, "code", d.code);
        write(gen, "old", de.oldEl);
        write(gen, "new", de.newEl);
        write(gen, "name", d.name);
        write(gen, "description", d.description);
        gen.writeStartArray("classification");
        for (Map.Entry<CompatibilityType, DifferenceSeverity> e : d.classification.entrySet()) {
            gen.writeStartObject();
            gen.write("compatibility", e.getKey().toString());
            gen.write("severity", e.getValue().toString());
            gen.writeEnd();
        }
        gen.writeEnd();
        gen.writeStartArray("attachments");
        for (Map.Entry<String, String> e : d.attachments.entrySet()) {
            gen.writeStartObject();
            gen.write("name", e.getKey());
            write(gen, "value", e.getValue());
            gen.writeEnd();
        }
        gen.writeEnd();
        gen.writeEnd();
    }

    private static void write(JsonGenerator gen, String key, @Nullable String value) {
        if (value == null) {
            gen.writeNull(key);
        } else {
            gen.write(key, value);
        }
    }

    private static class DifferenceWithElements {
        final String oldEl;
        final String newEl;
        final Difference diff;

        private DifferenceWithElements(String oldEl, String newEl, Difference diff) {
            this.oldEl = oldEl;
            this.newEl = newEl;
            this.diff = diff;
        }
    }
}
