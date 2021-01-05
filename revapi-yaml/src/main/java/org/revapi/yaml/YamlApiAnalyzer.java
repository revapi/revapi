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
package org.revapi.yaml;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.revapi.API;
import org.revapi.ArchiveAnalyzer;
import org.revapi.DifferenceAnalyzer;
import org.revapi.jackson.JacksonApiAnalyzer;

public class YamlApiAnalyzer extends JacksonApiAnalyzer<YamlElement> {
    public YamlApiAnalyzer() {
        super(new YAMLMapper());
    }

    @Override
    public String getExtensionId() {
        return "revapi.yaml";
    }

    @Override
    public ArchiveAnalyzer<YamlElement> getArchiveAnalyzer(@Nonnull API api) {
        return new YamlArchiveAnalyzer(this, api, pathMatcher, objectMapper, charset);
    }

    @Override
    public DifferenceAnalyzer<YamlElement> getDifferenceAnalyzer(ArchiveAnalyzer<YamlElement> oldArchive,
            ArchiveAnalyzer<YamlElement> newArchive) {
        return new YamlDifferenceAnalyzer();
    }
}
