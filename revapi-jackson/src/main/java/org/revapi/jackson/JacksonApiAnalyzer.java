/*
 * Copyright 2014-2021 Lukas Krejci
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
package org.revapi.jackson;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.revapi.AnalysisContext;
import org.revapi.CorrespondenceComparatorDeducer;
import org.revapi.base.BaseApiAnalyzer;

public abstract class JacksonApiAnalyzer<E extends JacksonElement<E>> extends BaseApiAnalyzer<E> {
    @Nullable
    protected Pattern pathMatcher;
    protected Charset charset;
    protected final ObjectMapper objectMapper;
    private final CorrespondenceComparatorDeducer<E> diff;

    public JacksonApiAnalyzer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.charset = StandardCharsets.UTF_8;
        this.diff = CorrespondenceComparatorDeducer.diff(Objects::equals);
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("/META-INF/revapi-jackson-config-schema.json"),
                StandardCharsets.UTF_8);
    }

    @Override
    public void initialize(AnalysisContext analysisContext) {
        JsonNode charsetNode = analysisContext.getConfigurationNode().path("charset");
        JsonNode pattern = analysisContext.getConfigurationNode().path("pathRegex");

        if (charsetNode.isTextual()) {
            this.charset = Charset.forName(charsetNode.asText());
        }

        if (pattern.isTextual()) {
            this.pathMatcher = Pattern.compile(pattern.asText());
        }
    }

    @Override
    public CorrespondenceComparatorDeducer<E> getCorrespondenceDeducer() {
        // let's try to be clever with the arrays and find the optimal edits to get the two arrays to the same state
        return (as, bs) -> {
            if (as.isEmpty() || bs.isEmpty()) {
                return Comparator.naturalOrder();
            }

            // the elements in the supplied lists each have a common parent... so we take a look at the parent to
            // figure out if we're looking at an array or object or value
            E aParent = as.get(0).getParent();
            if (aParent == null || !aParent.getNode().isArray()) {
                return Comparator.naturalOrder();
            }

            E bParent = bs.get(0).getParent();
            if (bParent == null || !bParent.getNode().isArray()) {
                return Comparator.naturalOrder();
            }

            // k, we have 2 arrays.. we want to compare them in a diff-like manner...
            return diff.sortAndGetCorrespondenceComparator(as, bs);
        };
    }

    @Override
    public void close() {
    }
}
