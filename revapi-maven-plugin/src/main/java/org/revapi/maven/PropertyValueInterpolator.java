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

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

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
 * This serves no other purpose but to make available the standard value sources for interpolating the properites
 * in the Revapi configuration files.
 */
public final class PropertyValueInterpolator {
    private static final List<String> PROJECT_PREFIXES = asList("project.", "pom.");

    private final List<ValueSource> valueSources;

    public PropertyValueInterpolator(Properties props) {
        valueSources = singletonList(new PropertiesBasedValueSource(props));
    }

    public PropertyValueInterpolator(MavenProject project) {
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
            throw new IllegalStateException("Could not construct environment variable value source for property interpolation.");
        }

    }

    public String interpolate(String name) {
        return valueSources.stream()
                .map(s -> s.getValue(name))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .findFirst().orElse(null);
    }
}
