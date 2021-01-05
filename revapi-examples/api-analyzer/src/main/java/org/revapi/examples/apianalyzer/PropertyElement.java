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

import java.util.Objects;

import org.revapi.API;
import org.revapi.Archive;
import org.revapi.base.BaseElement;

/**
 * This class represents a property as a Revapi element. On top of the key-value pair of the property itself,
 * it references the API and archive (i.e. the property file) it comes from.
 */
public class PropertyElement extends BaseElement<PropertyElement> {
    private final String key;
    private final String value;

    public PropertyElement(API api, Archive archive, String key, String value) {
        super(api, archive);
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    @Override
    public int compareTo(PropertyElement other) {
        // we only compare the keys, not values, here so that Revapi considers 2 elements with the same key equivalent
        // and we can then analyze them for value change in the PropertiesDifferenceAnalyzer
        return key.compareTo(other.key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PropertyElement that = (PropertyElement) o;
        return key.equals(that.key) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }
}
