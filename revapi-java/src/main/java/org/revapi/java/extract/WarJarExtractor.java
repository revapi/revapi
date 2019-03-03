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
package org.revapi.java.extract;

import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toList;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
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
    private List<Pattern> includes;
    private List<Pattern> excludes;

    @Override
    public Optional<InputStream> extract(Archive archive) {
        if (doNothing || !tryExtract(archive.getName())) {
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
                int prefixLen = "WEB-INF/classes/".length();
                while (inEntry != null) {
                    if (inEntry.getName().startsWith("WEB-INF/classes/") && inEntry.getName().length() > prefixLen) {
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
                Charset.forName("UTF-8"));
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        ModelNode include = analysisContext.getConfiguration().get("include");
        ModelNode exclude = analysisContext.getConfiguration().get("exclude");
        ModelNode disabled = analysisContext.getConfiguration().get("disabled");

        doNothing = disabled.isDefined() && disabled.asBoolean();

        if (include.isDefined() && include.getType() == ModelType.LIST) {
            List<ModelNode> includes = include.asList();
            this.includes = includes.stream().map(n -> compile(n.asString())).collect(toList());
        } else {
            this.includes = Collections.emptyList();
        }

        if (exclude.isDefined() && exclude.getType() == ModelType.LIST) {
            List<ModelNode> excludes = exclude.asList();
            this.excludes = excludes.stream().map(n -> compile(n.asString())).collect(toList());
        } else {
            this.excludes = Collections.emptyList();
        }
    }

    private boolean tryExtract(String archiveName) {
        boolean excluded = excludes.stream().anyMatch(p -> p.matcher(archiveName).matches());

        if (excluded) {
            return false;
        }

        if (includes.isEmpty()) {
            return true;
        } else {
            return includes.stream().anyMatch(p -> p.matcher(archiveName).matches());
        }
    }
}
