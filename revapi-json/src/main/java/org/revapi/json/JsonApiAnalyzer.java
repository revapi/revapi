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
package org.revapi.json;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.revapi.API;
import org.revapi.ArchiveAnalyzer;
import org.revapi.DifferenceAnalyzer;
import org.revapi.jackson.JacksonApiAnalyzer;

public class JsonApiAnalyzer extends JacksonApiAnalyzer<JsonElement> {
    public JsonApiAnalyzer() {
        super(new ObjectMapper());
    }

    @Override
    public String getExtensionId() {
        return "revapi.json";
    }

    @Override
    public ArchiveAnalyzer<JsonElement> getArchiveAnalyzer(@Nonnull API api) {
        return new JsonArchiveAnalyzer(this, api, pathMatcher, objectMapper, charset);
    }

    @Override
    public DifferenceAnalyzer<JsonElement> getDifferenceAnalyzer(ArchiveAnalyzer<JsonElement> oldArchive,
            ArchiveAnalyzer<JsonElement> newArchive) {
        return new JsonDifferenceAnalyzer();
    }
}
