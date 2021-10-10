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

import java.net.URI;

import javax.annotation.Nullable;

import org.revapi.jackson.JacksonDifferenceAnalyzer;

public class YamlDifferenceAnalyzer extends JacksonDifferenceAnalyzer<YamlElement> {
    @Override
    protected String valueRemovedCode() {
        return "yaml.removed";
    }

    @Override
    protected String valueAddedCode() {
        return "yaml.added";
    }

    @Override
    protected String valueChangedCode() {
        return "yaml.valueChanged";
    }

    @Nullable
    @Override
    protected URI documentationLinkForCode(String code) {
        return URI.create("https://revapi.org/revapi-yaml/index.html#" + code);
    }
}
