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
package org.revapi.java.compilation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import javax.lang.model.element.NestingKind;
import javax.tools.SimpleJavaFileObject;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
final class ArchiveProbeObject extends SimpleJavaFileObject {
    public static final String CLASS_NAME = "Probe";

    private String source;

    public ArchiveProbeObject() {
        super(getSourceFileName(), Kind.SOURCE);
    }

    private static URI getSourceFileName() {
        try {
            return new URI(CLASS_NAME + ".java");
        } catch (URISyntaxException e) {
            //doesn't happen
            throw new AssertionError("Could not create a URI for " + (CLASS_NAME + ".java"));
        }
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        generateIfNeeded();
        return source;
    }

    @Override
    public NestingKind getNestingKind() {
        return NestingKind.TOP_LEVEL;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        generateIfNeeded();
        return new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        generateIfNeeded();
        return new StringReader(source);
    }

    private void generateIfNeeded() throws IOException {
        if (source != null) {
            return;
        }

        //notice that we don't actually need to generate any complicated code. Having the classes on the classpath
        //is enough for them to be present in the model captured during the annotation processing.
        source = "@" + MarkerAnnotationObject.CLASS_NAME + "\npublic class " + CLASS_NAME + "\n{}\n";
    }
}
