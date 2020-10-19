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
package org.revapi.maven;

/**
 * A helper to {@link BuildTimeReporter} that outputs the ignore suggestions in JSON format.
 */
public class JsonSuggestionsBuilder extends AbstractSuggestionsBuilder {
    @Override
    protected void appendDifferenceField(StringBuilder sb, String key, String value) {
        sb.append("  \"").append(escape(key)).append("\": \"").append(escape(value)).append("\"");
    }

    @Override
    protected void appendDifferenceFieldSeparator(StringBuilder sb) {
        sb.append(",\n");
    }

    @Override
    protected void prologue(StringBuilder sb) {
    }

    @Override
    protected void startDifference(StringBuilder sb) {
        sb.append("{\n");
    }

    @Override
    protected void endDifference(StringBuilder sb) {
        sb.append("\n},\n");
    }

    @Override
    protected void startOptionalAttachmentsInComment(StringBuilder sb, String text) {
        sb.append("\n  /*");
    }

    @Override
    protected void endOptionalAttachmentsInComment(StringBuilder sb) {
        sb.append("  */\n");
    }

    @Override
    protected void epilogue(StringBuilder sb) {
    }

    private static String escape(Object obj) {
        if (obj == null) {
            return "null";
        }

        String string = obj.toString();

        char c;
        int i;
        int len = string.length();
        StringBuilder sb = new StringBuilder(len);
        String t;

        for (i = 0; i < len; i += 1) {
            c = string.charAt(i);
            switch (c) {
            case '\\':
            case '"':
                sb.append('\\');
                sb.append(c);
                break;
            case '/':
                sb.append('\\');
                sb.append(c);
                break;
            case '\b':
                sb.append("\\b");
                break;
            case '\t':
                sb.append("\\t");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\f':
                sb.append("\\f");
                break;
            case '\r':
                sb.append("\\r");
                break;
            default:
                if (c < ' ') {
                    t = "000" + Integer.toHexString(c);
                    sb.append("\\u").append(t.substring(t.length() - 4));
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
