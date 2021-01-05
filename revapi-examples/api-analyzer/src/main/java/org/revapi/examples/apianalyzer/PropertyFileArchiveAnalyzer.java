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
package org.revapi.examples.apianalyzer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.revapi.API;
import org.revapi.base.BaseArchiveAnalyzer;
import org.revapi.base.BaseElementForest;

/**
 * An archive analyzer is created by the API analyzer for a given API. It is responsible for turning the archives of
 * the API into a tree of API elements. This usually means that one also needs to create dedicated types of elements for
 * given API analyzer.
 */
public class PropertyFileArchiveAnalyzer extends BaseArchiveAnalyzer<BaseElementForest<PropertyElement>, PropertyElement> {
    public PropertyFileArchiveAnalyzer(API api, PropertiesAnalyzer analyzer) {
        super(analyzer, api);
    }

    // we could have opted to define our own type for the element forest of properties, but we can also get away
    // with just the parameterized base type.
    @Override
    protected BaseElementForest<PropertyElement> newElementForest() {
        return new BaseElementForest<>(getApi());
    }

    @Override
    protected Stream<PropertyElement> discoverRoots(@Nullable Object context) {
        return StreamSupport.stream(getApi().getArchives().spliterator(), false)
                .flatMap(archive -> {
                    try (InputStream is = archive.openStream()) {
                        Properties props = new Properties();
                        props.load(is);
                        List<PropertyElement> elements = new ArrayList<>(props.size());
                        for (String name : props.stringPropertyNames()) {
                            // the properties don't have any hierarchy in our simple example, so we put everything in as
                            // roots
                            PropertyElement pe = new PropertyElement(getApi(), archive, name, props.getProperty(name));
                            elements.add(pe);
                        }

                        return elements.stream();
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Failed to read archive " + archive.getName()
                                + " as properties file.", e);
                    }
                });
    }

    @Override
    protected Stream<PropertyElement> discoverElements(@Nullable Object context, PropertyElement parent) {
        // in this example, our property elements don't have any hierarchy, so this is always empty
        return Stream.empty();
    }
}
