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
package org.revapi.java.extract;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.java.spi.JarExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * If the provided archive is a ZIP file (which also means a JAR file) and it contains entries in the WEB-INF/classes
 * directory then only those entries are considered for API analysis.
 *
 * <p>Can be configured using {@code include} and {@code exclude} lists of regexes on archive names so that user can
 * switch this extractor off if need be.
 *
 * <p><b>Extension ID:</b> {@code war}
 */
public class WarJarExtractor implements JarExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(WarJarExtractor.class);

    private boolean doNothing;
    private Map<Pattern, Set<String>> scan;

    @Override
    public Optional<InputStream> extract(Archive archive) {
        if (doNothing) {
            return Optional.empty();
        }

        Set<String> pathPatterns = getPrefixesToExtract(archive.getName());
        if (pathPatterns.isEmpty()) {
            return Optional.empty();
        }

        Path path = null;
        try (ZipInputStream orig = new ZipInputStream(archive.openStream())) {
            path = Files.createTempFile("revapi-java-jarextract-war", null);

            boolean isWarFile = false;
            try (ZipOutputStream croppedZip = new ZipOutputStream(new FileOutputStream(path.toFile()))) {
                croppedZip.setLevel(Deflater.NO_COMPRESSION);
                croppedZip.setMethod(ZipOutputStream.DEFLATED);

                byte[] buf = new byte[32768];

                ZipEntry inEntry = orig.getNextEntry();
                while (inEntry != null) {
                    int prefixLen = getMatchedPathPrefixLength(inEntry.getName(), pathPatterns);
                    if (prefixLen >= 0) {
                        isWarFile = true;
                        ZipEntry outEntry = new ZipEntry(inEntry.getName().substring(prefixLen));

                        croppedZip.putNextEntry(outEntry);

                        if (!inEntry.isDirectory()) {
                            int cnt;
                            while ((cnt = orig.read(buf)) != -1) {
                                croppedZip.write(buf, 0, cnt);
                            }
                        }

                        croppedZip.closeEntry();
                    }

                    inEntry = orig.getNextEntry();
                }
            }

            if (isWarFile) {
                Path finalPath = path;
                return Optional.of(new FileInputStream(path.toFile()) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        Files.delete(finalPath);
                    }
                });
            } else {
                cleanPath(path);
                return Optional.empty();
            }
        } catch (IOException e) {
            cleanPath(path);
            return Optional.empty();
        }
    }

    private int getMatchedPathPrefixLength(String entryName, Set<String> prefixes) {
        for (String prefix : prefixes) {
            if (entryName.startsWith(prefix)) {
                return prefix.length();
            }
        }

        return  -1;
    }

    private void cleanPath(Path path) {
        if (path != null) {
            try {
                Files.delete(path);
            } catch (IOException e1) {
                LOG.warn("Failed to delete temporary file " + path, e1);
            }
        }
    }

    @Override
    public String getExtensionId() {
        return "war";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("/META-INF/warJarExtract-config-schema.json"),
                StandardCharsets.UTF_8);
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        JsonNode scan = analysisContext.getConfigurationNode().path("scan");
        JsonNode disabled = analysisContext.getConfigurationNode().path("disabled");

        doNothing = disabled.asBoolean(false);

        if (doNothing) {
            return;
        }

        if (scan.isArray()) {
            this.scan = new HashMap<>(scan.size(), 1f);
            for(JsonNode record : scan) {
                JsonNode archiveNode = record.path("archive");
                JsonNode prefixesNode = record.path("prefixes");

                if (archiveNode.isMissingNode() || prefixesNode.isMissingNode()) {
                    continue;
                }

                if (!archiveNode.isTextual() || !prefixesNode.isArray()) {
                    continue;
                }

                Pattern archive = Pattern.compile(archiveNode.asText());
                Set<String> prefixes = StreamSupport.stream(prefixesNode.spliterator(), false)
                        .map(JsonNode::asText)
                        .map(v -> v.endsWith("/") ? v : (v + "/"))
                        .collect(toSet());

                this.scan.put(archive, prefixes);
            }
        } else {
            // set the default scan
            this.scan = new HashMap<>(1, 1f);
            this.scan.put(Pattern.compile(".*"), new HashSet<>(singletonList("/WEB-INF/classes/")));
        }
    }

    private Set<String> getPrefixesToExtract(String archiveName) {
        Set<String> ret = null;
        for (Map.Entry<Pattern, Set<String>> e : scan.entrySet()) {
            if (e.getKey().matcher(archiveName).matches()) {
                if (ret == null) {
                    ret = new HashSet<>(e.getValue());
                } else {
                    ret.addAll(e.getValue());
                }
            }
        }

        return ret == null ? emptySet() : ret;
    }
}
