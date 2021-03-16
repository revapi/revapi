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

/**
 * A helper to {@link BuildTimeReporter} that outputs the ignore suggestions in XML format.
 */
public class XmlSuggestionsBuilder extends AbstractSuggestionsBuilder {
    @Override
    protected void appendDifferenceField(StringBuilder sb, String key, Object value) {
        sb.append("  <").append(key).append(">").append(escape(value)).append("</").append(key).append(">");
    }

    @Override
    protected void appendDifferenceFieldSeparator(StringBuilder sb) {
        sb.append("\n");
    }

    @Override
    protected void prologue(StringBuilder sb) {

    }

    @Override
    protected void startDifference(StringBuilder sb) {
        sb.append("<item>\n");
    }

    @Override
    protected void endDifference(StringBuilder sb) {
        sb.append("\n</item>\n");
    }

    @Override
    protected void startOptionalAttachmentsInComment(StringBuilder sb, String text) {
        sb.append("\n  <!-- ").append(text);
    }

    @Override
    protected void endOptionalAttachmentsInComment(StringBuilder sb) {
        sb.append("  -->");
    }

    @Override
    protected void epilogue(StringBuilder sb) {

    }

    private static String escape(Object value) {
        if (value == null) {
            return "";
        } else {
            return value.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
