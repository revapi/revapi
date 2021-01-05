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

import java.nio.charset.Charset;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.revapi.API;
import org.revapi.Archive;
import org.revapi.jackson.JacksonArchiveAnalyzer;

public class JsonArchiveAnalyzer extends JacksonArchiveAnalyzer<JsonElement> {
    public JsonArchiveAnalyzer(JsonApiAnalyzer apiAnalyzer, API api, Pattern pathMatcher, ObjectMapper objectMapper,
            Charset charset) {
        super(apiAnalyzer, api, pathMatcher, objectMapper, charset);
    }

    @Override
    protected JsonElement toElement(Archive archive, String filePath, TreeNode node, String keyInParent) {
        return new JsonElement(getApi(), archive, filePath, node, keyInParent);
    }

    @Override
    protected JsonElement toElement(Archive archive, String filePath, TreeNode node, int indexInParent) {
        return new JsonElement(getApi(), archive, filePath, node, indexInParent);
    }
}
