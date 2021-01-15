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

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.SortedSet;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.revapi.API;
import org.revapi.Archive;
import org.revapi.base.BaseElementForest;
import org.revapi.base.ZipArchiveAnalyzer;

public abstract class JacksonArchiveAnalyzer<E extends JacksonElement<E>> extends ZipArchiveAnalyzer<BaseElementForest<E>, E> {
    private final ObjectMapper objectMapper;
    private final Charset charset;

    protected JacksonArchiveAnalyzer(JacksonApiAnalyzer<E> apiAnalyzer, API api, @Nullable Pattern pathMatcher,
            ObjectMapper objectMapper, Charset charset) {
        super(apiAnalyzer, api, pathMatcher == null ? emptyList() : singletonList(pathMatcher));
        this.objectMapper = objectMapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);
        this.charset = charset;
    }

    @Override
    protected BaseElementForest<E> newElementForest() {
        return new BaseElementForest<>(getApi());
    }

    @Override
    protected Set<E> createElements(Archive a, @Nullable ZipEntry entry, InputStream data) throws IOException {
        JsonNode tree = parseStream(data);
        String filePath = entry == null ? a.getName() : entry.getName();

        E root = toElement(a, filePath, tree, filePath);
        if (tree.isArray()) {
            for (int idx = 0; idx < tree.size(); ++idx) {
                add(a, root.filePath, root.getChildren(), tree.get(idx), idx);
            }
        } else if (tree.isObject()) {
            tree.fieldNames().forEachRemaining(f -> add(a, root.filePath, root.getChildren(), tree.get(f), f));
        }

        return singleton(root);
    }

    private JsonNode parseStream(InputStream data) throws IOException {
        Reader rdr = new InputStreamReader(data, charset);
        return objectMapper.readTree(rdr);
    }

    private void add(E el, SortedSet<E> siblings) {
        siblings.add(el);

        TreeNode tree = el.node;
        if (tree.isArray()) {
            for (int idx = 0; idx < tree.size(); ++idx) {
                add(el.getArchive(), el.filePath, el.getChildren(), tree.get(idx), idx);
            }
        } else if (tree.isObject()) {
            tree.fieldNames().forEachRemaining(f -> add(el.getArchive(), el.filePath, el.getChildren(), tree.get(f), f));
        }
    }

    private void add(Archive archive, String filePath, SortedSet<E> siblings, TreeNode tree, String keyInParent) {
        E el = toElement(archive, filePath, tree, keyInParent);
        add(el, siblings);
    }

    private void add(Archive archive, String filePath, SortedSet<E> siblings, TreeNode tree, int indexInParent) {
        E el = toElement(archive, filePath, tree, indexInParent);
        add(el, siblings);
    }

    protected abstract E toElement(Archive archive, String filePath, TreeNode node, String keyInParent);

    protected abstract E toElement(Archive archive, String filePath, TreeNode node, int indexInParent);
}
