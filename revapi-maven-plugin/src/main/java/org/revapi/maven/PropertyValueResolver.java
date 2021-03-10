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

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.maven.model.Model;
import org.apache.maven.model.interpolation.MavenBuildTimestamp;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.interpolation.AbstractValueSource;
import org.codehaus.plexus.interpolation.EnvarBasedValueSource;
import org.codehaus.plexus.interpolation.MapBasedValueSource;
import org.codehaus.plexus.interpolation.ObjectBasedValueSource;
import org.codehaus.plexus.interpolation.PrefixedObjectValueSource;
import org.codehaus.plexus.interpolation.PrefixedValueSourceWrapper;
import org.codehaus.plexus.interpolation.PropertiesBasedValueSource;
import org.codehaus.plexus.interpolation.ValueSource;

/**
 * This serves no other purpose but to make available the standard value sources for interpolating the properties in the
 * Revapi configuration files.
 */
public final class PropertyValueResolver {
    private static final List<String> PROJECT_PREFIXES = asList("project.", "pom.");
    private static final Pattern VAR_DETECTOR = Pattern.compile("\\$\\{.+}");

    // state machine states
    private static final int INITIAL = 0;
    private static final int GOT_DOLLAR = 1;
    private static final int GOT_OPEN_BRACE = 2;
    private static final int RESOLVED = 3;
    private static final int DEFAULT = 4;

    private final List<ValueSource> valueSources;

    public PropertyValueResolver(Properties props) {
        this(singletonList(new PropertiesBasedValueSource(props)));
    }

    public PropertyValueResolver(List<ValueSource> valueSources) {
        this.valueSources = valueSources;
    }

    public PropertyValueResolver(MavenProject project) {
        // this is more or less copied from org.apache.maven.model.interpolation.AbstractStringBasedModelInterpolator
        this.valueSources = new ArrayList<>(8);

        MavenBuildTimestamp now = new MavenBuildTimestamp();

        Model model = project.getModel();
        Properties modelProperties = model.getProperties();
        File projectDir = project.getBasedir();

        ValueSource prefixedProjectValues = new PrefixedObjectValueSource(PROJECT_PREFIXES, model, false);
        valueSources.add(prefixedProjectValues);

        ValueSource unprefixedProjectValues = new ObjectBasedValueSource(model);
        valueSources.add(unprefixedProjectValues);

        ValueSource basedirValueSource = new PrefixedValueSourceWrapper(new AbstractValueSource(false) {
            public Object getValue(String expression) {
                if ("basedir".equals(expression)) {
                    return projectDir.getAbsolutePath();
                }
                return null;
            }
        }, PROJECT_PREFIXES, true);
        valueSources.add(basedirValueSource);

        ValueSource baseUriValueSource = new PrefixedValueSourceWrapper(new AbstractValueSource(false) {
            public Object getValue(String expression) {
                if ("baseUri".equals(expression)) {
                    return projectDir.getAbsoluteFile().toURI().toString();
                }
                return null;
            }
        }, PROJECT_PREFIXES, false);
        valueSources.add(baseUriValueSource);

        ValueSource buildTimeStampValueSource = new PrefixedValueSourceWrapper(new AbstractValueSource(false) {
            public Object getValue(String expression) {
                if ("build.timestamp".equals(expression)) {
                    return now.formattedTimestamp();
                }
                return null;
            }
        }, singletonList("maven."), true);
        valueSources.add(buildTimeStampValueSource);

        valueSources.add(new MapBasedValueSource(System.getProperties()));

        valueSources.add(new MapBasedValueSource(modelProperties));

        try {
            valueSources.add(new EnvarBasedValueSource(false));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not construct environment variable value source for property interpolation.");
        }

    }

    public boolean containsVariables(CharSequence expression) {
        return VAR_DETECTOR.matcher(expression).find();
    }

    // This is shamelessly copied from ValueExpressionResolver of JBoss DMR. Only slight modifications were made
    // to avoid calling String.substring() where possible
    public String resolve(CharSequence expression) {
        final String expr = expression.toString();
        final StringBuilder builder = new StringBuilder();
        final int len = expr.length();
        int state = INITIAL;
        int start = -1;
        int nest = 0;
        int nameStart = -1;
        for (int i = 0; i < len; i = expr.offsetByCodePoints(i, 1)) {
            final int ch = expr.codePointAt(i);
            switch (state) {
            case INITIAL: {
                switch (ch) {
                case '$': {
                    state = GOT_DOLLAR;
                    continue;
                }
                default: {
                    builder.appendCodePoint(ch);
                    continue;
                }
                }
                // not reachable
            }
            case GOT_DOLLAR: {
                switch (ch) {
                case '$': {
                    builder.appendCodePoint(ch);
                    state = INITIAL;
                    continue;
                }
                case '{': {
                    start = i + 1;
                    nameStart = start;
                    state = GOT_OPEN_BRACE;
                    continue;
                }
                default: {
                    // invalid; emit and resume
                    builder.append('$').appendCodePoint(ch);
                    state = INITIAL;
                    continue;
                }
                }
                // not reachable
            }
            case GOT_OPEN_BRACE: {
                switch (ch) {
                case '{': {
                    nest++;
                    continue;
                }
                case ':':
                    if (nameStart == i) {
                        // not a default delimiter; same as default case
                        continue;
                    }
                    // else fall into the logic for 'end of key to resolve cases' "," and "}"
                case '}':
                case ',': {
                    if (nest > 0) {
                        if (ch == '}') {
                            nest--;
                        }
                        continue;
                    }
                    final String val = findValue(expr.substring(nameStart, i).trim());
                    if (val != null && !val.equals(expr)) {
                        builder.append(val);
                        state = ch == '}' ? INITIAL : RESOLVED;
                        continue;
                    } else if (ch == ',') {
                        nameStart = i + 1;
                        continue;
                    } else if (ch == ':') {
                        start = i + 1;
                        state = DEFAULT;
                        continue;
                    } else {
                        throw new IllegalStateException(
                                "Failed to resolve expression: " + expr.substring(start - 2, i + 1));
                    }
                }
                default: {
                    continue;
                }
                }
                // not reachable
            }
            case RESOLVED: {
                if (ch == '{') {
                    nest++;
                } else if (ch == '}') {
                    if (nest > 0) {
                        nest--;
                    } else {
                        state = INITIAL;
                    }
                }
                continue;
            }
            case DEFAULT: {
                if (ch == '{') {
                    nest++;
                } else if (ch == '}') {
                    if (nest > 0) {
                        nest--;
                    } else {
                        state = INITIAL;
                        builder.append(expr, start, i);
                    }
                }
                continue;
            }
            default:
                throw new IllegalStateException("Unexpected char seen: " + ch);
            }
        }
        switch (state) {
        case GOT_DOLLAR: {
            builder.append('$');
            break;
        }
        case DEFAULT: {
            builder.append(expr, start - 2, expr.length());
            break;
        }
        case GOT_OPEN_BRACE: {
            // We had a reference that was not resolved, throw ISE
            throw new IllegalStateException("Incomplete expression: " + builder.toString());
        }
        }
        return builder.toString();
    }

    private @Nullable String findValue(String name) {
        return valueSources.stream().map(s -> s.getValue(name)).filter(Objects::nonNull).map(Object::toString)
                .findFirst().orElse(null);
    }
}
