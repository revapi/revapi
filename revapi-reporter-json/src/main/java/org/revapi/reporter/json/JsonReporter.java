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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.revapi.AnalysisContext;
import org.revapi.CompatibilityType;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.Report;
import org.revapi.reporter.file.AbstractFileReporter;

public class JsonReporter extends AbstractFileReporter {
    private Set<Report> reports;
    private boolean prettyPrint;

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
        prettyPrint = analysisContext.getConfigurationNode().path("indent").asBoolean(false);
        this.reports = new TreeSet<>(getReportsByElementOrderComparator());
    }

    @Override
    protected void flushReports() {
        try {
            JsonGenerator jsonGenerator = createGenerator();
            jsonGenerator.writeStartArray();
            reports.stream().flatMap(r -> {
                String oldEl = r.getOldElement() == null ? null : r.getOldElement().getFullHumanReadableString();
                String newEl = r.getNewElement() == null ? null : r.getNewElement().getFullHumanReadableString();

                return r.getDifferences().stream().map(d -> new DifferenceWithElements(oldEl, newEl, d));
            }).forEach(d -> {
                try {
                    writeDifference(jsonGenerator, d);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to write the output.", e);
                }
            });
            jsonGenerator.writeEndArray();
            jsonGenerator.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write the output.", e);
        }
    }

    @Override
    protected void doReport(Report report) {
        reports.add(report);
    }

    private static void writeDifference(JsonGenerator gen, DifferenceWithElements de) throws IOException {
        gen.writeStartObject();
        Difference d = de.diff;
        write(gen, "code", d.code);
        write(gen, "old", de.oldEl);
        write(gen, "new", de.newEl);
        write(gen, "name", d.name);
        write(gen, "description", d.description);
        gen.writeArrayFieldStart("classification");
        for (Map.Entry<CompatibilityType, DifferenceSeverity> e : d.classification.entrySet()) {
            gen.writeStartObject();
            gen.writeStringField("compatibility", e.getKey().toString());
            gen.writeStringField("severity", e.getValue().toString());
            gen.writeEndObject();
        }
        gen.writeEndArray();
        gen.writeArrayFieldStart("attachments");
        for (Map.Entry<String, String> e : d.attachments.entrySet()) {
            gen.writeStartObject();
            gen.writeStringField("name", e.getKey());
            write(gen, "value", e.getValue());
            gen.writeEndObject();
        }
        gen.writeEndArray();
        gen.writeEndObject();
    }

    private JsonGenerator createGenerator() throws IOException {
        JsonGenerator jsonGenerator = JsonFactory.builder().build().createGenerator(output);
        if (prettyPrint) {
            jsonGenerator.useDefaultPrettyPrinter();
        }

        return jsonGenerator;
    }

    private static void write(JsonGenerator gen, String key, @Nullable String value) throws IOException {
        if (value == null) {
            gen.writeNullField(key);
        } else {
            gen.writeStringField(key, value);
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
