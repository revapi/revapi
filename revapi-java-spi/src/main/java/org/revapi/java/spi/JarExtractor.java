/*
 * Copyright 2014-2019 Lukas Krejci
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
package org.revapi.java.spi;

import java.io.InputStream;
import java.util.Optional;

import org.revapi.Archive;
import org.revapi.configuration.Configurable;

/**
 * The role of the jar extractor is to find class files in an archive that should be considered for API analysis.
 *
 * <p>This is mainly meant for extracting the files from for example WAR files, etc, where the classes are nested within
 * a more complex directory structure.
 *
 * <p>This interface is a normal Revapi extension that further extends {@code revapi-java}. As such it is also
 * configurable. The configuration can be placed under {@code revapi.java.extract} root using the extension id of the
 * implementation. E.g. in XML:
 *
 * <pre>{@code
 * <revapi.java>
 *     <extract>
 *         <my.extractor.extension.id>
 *             ... config is here ...
 *         </my.extractor.extension.id>
 *     </extract>
 * </revapi.java>
 * }</pre>
 */
public interface JarExtractor extends Configurable {

    /**
     * Tries to transform the data of the archive such that it looks like a simple jar file to the analyzer. This method
     * can be difficult to implement because needs to inspect the contents of the provided input stream and "rewrap" it
     * in a different way if needed.
     *
     * <p>This method can theoretically base the transformation decisions on other metadata present on the provided
     * archive instance, but that is considered dangerous because it limits the reusability of the transformer between
     * different invocation types of Revapi (CLI vs Maven, etc, which may or may not use the same implementation of the
     * archive).
     *
     * <p>If the transformation is not possible the corresponding archive is supplied to the analysis in full.
     *
     * <p>Note that the returned stream, if any, <b>MUST</b> be independent of the provided {@code archiveData}. This
     * method need not close the {@code archive}.
     *
     * @param archive the archive to analyze
     * @return an optional transformation of the input stream of the archive Oas if it were a normal JAR file or an empty
     * optional if such transformation is not possible.
     */
    Optional<InputStream> extract(Archive archive);
}
