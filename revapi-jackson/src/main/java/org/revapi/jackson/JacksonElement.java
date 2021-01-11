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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import org.revapi.API;
import org.revapi.Archive;
import org.revapi.base.BaseElement;

/**
 * A base class for elements based on Jackson.
 * @param <E>
 */
public class JacksonElement<E extends JacksonElement<E>> extends BaseElement<E> {

    protected final String filePath;
    protected final TreeNode node;
    protected final String keyInParent;
    protected final int indexInParent;
    private String fullString;
    private String valueString;
    private String fullPathString;

    public JacksonElement(API api, Archive archive, String filePath, TreeNode node, String key) {
        this(api, archive, filePath, node, key, -1);
    }

    public JacksonElement(API api, Archive archive, String filePath, TreeNode node, int index) {
        this(api, archive, filePath, node, null, index);
    }

    private JacksonElement(API api, Archive archive, String filePath, TreeNode node, String key, int index) {
        super(api, archive);
        this.filePath = filePath;
        this.node = node;
        this.keyInParent = key;
        this.indexInParent = index;
    }

    public TreeNode getNode() {
        return node;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getPath() {
        if (fullPathString == null) {
            List<String> path = new ArrayList<>(6);

            String part = getPathPart();
            if (!part.isEmpty()) {
                path.add(part);
            }
            E parent = getParent();
            while (parent != null) {
                part = parent.getPathPart();
                if (!part.isEmpty()) {
                    path.add(part);
                }
                parent = parent.getParent();
            }

            StringBuilder sb = new StringBuilder();
            for(int i = path.size() - 1; i >= 0; --i) {
                sb.append('/').append(path.get(i));
            }

            fullPathString = sb.toString();
        }

        return fullPathString;
    }

    // This should be generic over any type of jackson-supported data format
    // so theoretically we just need a single element impl for JSON, YAML, CSV and anything
    // else Jackson supports...
    public String getValueString() {
        if (valueString == null && node.isValueNode()) {
            JsonParser p = node.traverse();
            try {
                p.nextToken();
                valueString = p.getText();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to reparse an already parsed tree node. Strange.", e);
            }
        }

        return valueString;
    }

    @Override
    public void setParent(@Nullable E parent) {
        super.setParent(parent);
        this.fullPathString = null;
        this.fullString = null;
    }

    @Nonnull
    @Override
    public String getFullHumanReadableString() {
        if (fullString == null) {
            fullString = createFullHumanReadableString();
        }
        return fullString;
    }

    protected String createFullHumanReadableString() {
        return filePath + ":" + getPath();
    }

    @Override
    public int compareTo(E other) {
        if (keyInParent != null) {
            if (other.keyInParent == null) {
                return other.indexInParent < 0 ? 1 : -1;
            } else {
                return keyInParent.compareTo(other.keyInParent);
            }
        } else {
            if (other.indexInParent < 0) {
                return -1;
            } else {
                return indexInParent - other.indexInParent;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        JacksonElement<?> that = (JacksonElement<?>) o;

        return node.equals(that.node);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node);
    }

    protected String getPathPart() {
        if (getParent() == null) {
            return "";
        } else if (keyInParent != null) {
            return keyInParent;
        } else if (indexInParent >= 0) {
            return Integer.toString(indexInParent);
        } else {
            return  "";
        }
    }
}
