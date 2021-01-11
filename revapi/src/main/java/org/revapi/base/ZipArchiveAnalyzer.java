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
package org.revapi.base;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nullable;

import org.revapi.API;
import org.revapi.ApiAnalyzer;
import org.revapi.Archive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a base class for archive analyzers that need to search for files in a zipped archive.
 *
 * <p>This implementation is only useful for API analyzers that process the files in the ZIP archives one-by-one in
 * some manner. E.g. they might be looking for some concrete files in the zip or can compose the API from looking at
 * the individual files. For analyzers that need to process the files as a whole, this might not be the ideal superclass
 * to inherit from.
 *
 * <p>This implementation ignores the {@link API#getSupplementaryArchives() supplementary archives} of the API.
 */
public abstract class ZipArchiveAnalyzer<F extends BaseElementForest<E>, E extends BaseElement<E>>
        extends BaseEagerLoadingArchiveAnalyzer<F, E> {

    private static final Logger LOG = LoggerFactory.getLogger(ZipArchiveAnalyzer.class);

    protected final List<Pattern> matchPatterns;

    /**
     *
     * @param apiAnalyzer the api analyzer for which this archive analyzer is created
     * @param api the API this analyzer analyzes
     * @param matchPatterns the match patterns for files looked for in the zip file
     */
    public ZipArchiveAnalyzer(ApiAnalyzer<E> apiAnalyzer, API api, List<Pattern> matchPatterns) {
        super(apiAnalyzer, api, false);
        this.matchPatterns = matchPatterns;
    }

    @Override
    protected Set<E> createElements(Archive archive) {
        Set<E> all = new HashSet<>();
        try (ZipInputStream in = new ZipInputStream(archive.openStream())) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (nameMatches(entry)) {
                    all.addAll(createElements(archive, entry, in));
                }
                in.closeEntry();
            }
        } catch (IOException e) {
            // let's just not try to open the archive again while we have it still open... If I remember correctly,
            // Windows might have trouble conforming...
            LOG.debug("Failed to read archive '" + archive + "' as a ZIP archive. Retrying as an uncompressed stream.", e);
            all.clear();
        }

        if (all.isEmpty() && matchPatterns.isEmpty()) {
            try (InputStream in2 = archive.openStream()) {
                all.addAll(createElements(archive, null, in2));
            } catch (IOException e2) {
                // well, we can't do much but to log and continue...
                LOG.warn("Failed to analyze archive '" + archive + "' both as a zip archive and as an uncompressed file.");
            }
        }

        return all;
    }

    /**
     * Creates elements out of the provided data. These element don't have to be placed in any kind of hierarchy -
     * that will be done automatically in {@link ZipArchiveAnalyzer} base implementation. If the returned elements
     * contain any sub-elements, they need to be added as children though.
     *
     * @param a the archive containing the entry
     * @param entry the zip file entry, can be null if parsing data of the whole file (in case it is not a ZIP file)
     * @param data the data of the entry
     * @return the parsed elements
     */
    protected abstract Set<E> createElements(Archive a, @Nullable ZipEntry entry, InputStream data) throws IOException;

    private boolean nameMatches(ZipEntry entry) {
        for (Pattern p : matchPatterns) {
            if (p.matcher(entry.getName()).matches()) {
                return true;
            }
        }

        return false;
    }
}
