/*
 * Copyright 2013 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi.java;

import org.revapi.Archive;
import org.revapi.ArchiveAnalyzer;
import org.revapi.ElementAnalyzer;
import org.revapi.Language;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public class JavaLanguage implements Language {

    @Override
    public ArchiveAnalyzer getArchiveAnalyzer(Archive archive) {
        if (archive.getName().toLowerCase().endsWith(".jar")) {
            return new JarAnalyzer(archive);
        } else if (archive.getName().toLowerCase().endsWith(".class")) {
            return new ClassFileAnalyzer(archive);
        } else {
            return null;
        }
    }

    @Override
    public ElementAnalyzer getElementAnalyzer() {
        return new JavaElementAnalyzer();
    }
}
