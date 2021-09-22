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
package org.revapi.maven;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfigurationException;

/**
 * @author Lukas Krejci
 * 
 * @since 0.9.0
 */
final class XmlUtil {

    private XmlUtil() {

    }

    static int estimateIndentationSize(BufferedReader xml) throws IOException {
        boolean inComment = false;

        Map<Integer, Integer> indentHist = new HashMap<>(2);

        String line;
        int prevIndent = 0;
        int prevNonWhiteSpaceIdx = 0;
        while ((line = xml.readLine()) != null) {
            if (inComment) {
                if (line.contains("-->")) {
                    inComment = false;
                }
            } else {
                int startCommentIdx = line.indexOf("<!--");
                int endCommentIdx = line.indexOf("-->");
                inComment = startCommentIdx >= 0 && endCommentIdx == -1;
                int nonWhitespaceIdx = -1;
                int maxCheckIdx = startCommentIdx >= 0 ? startCommentIdx : line.length();

                for (int i = 0; i < maxCheckIdx; ++i) {
                    if (!Character.isWhitespace(line.charAt(i))) {
                        nonWhitespaceIdx = i;
                        break;
                    }
                }

                if (nonWhitespaceIdx == -1) {
                    // empty line...
                    continue;
                }

                int indent = Math.abs(nonWhitespaceIdx - prevNonWhiteSpaceIdx);
                if (indent > 0) {
                    indentHist.compute(indent, (x, count) -> count == null ? 1 : count + 1);
                    prevIndent = indent;
                } else if (prevIndent != 0) {
                    indentHist.compute(prevIndent, (x, count) -> count == null ? 1 : count + 1);
                }
                prevNonWhiteSpaceIdx = nonWhitespaceIdx;
            }
        }

        // indentation size of 2 by default, otherwise what's in the file
        return indentHist.isEmpty() ? 2 : indentHist.entrySet().stream()
                .reduce(new SimpleEntry<>(0, 1), (a, b) -> a.getValue() > b.getValue() ? a : b).getKey();
    }

    static void toIndentedString(PlexusConfiguration xml, int indentationSize, int currentDepth, Writer wrt)
            throws IOException {

        indent(indentationSize, currentDepth, wrt);

        String content;
        try {
            content = xml.getValue();
        } catch (PlexusConfigurationException e) {
            throw new IllegalStateException("Failed to read configuration", e);
        }

        boolean hasChildren = xml.getChildCount() > 0;
        boolean hasContent = content != null && !content.isEmpty();
        wrt.write('<');
        wrt.write(xml.getName());
        if (!hasChildren && !hasContent) {
            wrt.write("/>");
        } else {
            wrt.write('>');
            if (hasChildren) {
                wrt.write('\n');
                for (PlexusConfiguration c : xml.getChildren()) {
                    toIndentedString(c, indentationSize, currentDepth + 1, wrt);
                    wrt.append('\n');
                }

                if (!hasContent) {
                    indent(indentationSize, currentDepth, wrt);
                }
            }

            if (hasContent) {
                escaped(wrt, content);
            }

            wrt.write("</");
            wrt.write(xml.getName());
            wrt.write('>');
        }
    }

    private static void indent(int indentationSize, int currentDepth, Writer wrt) throws IOException {
        int numSpaces = currentDepth * indentationSize;
        for (int i = 0; i < numSpaces; ++i) {
            wrt.write(' ');
        }
    }

    private static void escaped(Writer wrt, String value) throws IOException {
        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            switch (c) {
            case '&':
                wrt.write("&amp;");
                break;
            case '<':
                wrt.write("&lt;");
                break;
            case '>':
                wrt.write("&gt;");
                break;
            default:
                wrt.write(c);
            }
        }
    }
}
