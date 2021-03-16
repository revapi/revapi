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

import org.revapi.API;
import org.revapi.ArchiveAnalyzer;
import org.revapi.base.BaseApiAnalyzer;

/**
 * This is the main entry point implementing the analyzer of property files.
 */
public class PropertiesAnalyzer extends BaseApiAnalyzer<PropertyElement> {

    @Override
    public PropertyFileArchiveAnalyzer getArchiveAnalyzer(API api) {
        return new PropertyFileArchiveAnalyzer(api, this);
    }

    @Override
    public PropertiesDifferenceAnalyzer getDifferenceAnalyzer(ArchiveAnalyzer<PropertyElement> oldAnalyzer,
            ArchiveAnalyzer<PropertyElement> newAnalyzer) {
        return new PropertiesDifferenceAnalyzer();
    }

    @Override
    public String getExtensionId() {
        return "properties";
    }
}
