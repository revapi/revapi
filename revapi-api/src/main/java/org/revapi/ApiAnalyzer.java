/*
 * Copyright 2014 Lukas Krejci
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

package org.revapi;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public interface ApiAnalyzer {

    void initialize(Configuration configuration);

    /**
     * This method is called exactly twice during the API difference analysis. The first time it is called to obtain
     * an archive analyzer for the old version of the archives and the second time for the new version of the archives.
     *
     * @param archives              the archives to obtain an analyzer for
     * @param supplementaryArchives the set of archives that somehow supplement the main ones (for example they contain
     *                              definitions used in the main archives). In Java, supplementary archives would be
     *                              the JARs that need to be on the compilation classpath. Can be null if no such
     *                              archives are needed.
     * @return the analyzer for the supplied archives
     */
    ArchiveAnalyzer getArchiveAnalyzer(Iterable<? extends Archive> archives,
        Iterable<? extends Archive> supplementaryArchives);

    /**
     * This method is called exactly once during the API difference analysis and produces an element analyzer which
     * will
     * be used to compare the corresponding elements in the old and new archives.
     *
     * @param oldArchive the analyzer used for the old archives
     * @param newArchive the analyzer used for the new archives
     *
     * @return an element analyzer
     */
    ElementAnalyzer getElementAnalyzer(ArchiveAnalyzer oldArchive, ArchiveAnalyzer newArchive);
}
