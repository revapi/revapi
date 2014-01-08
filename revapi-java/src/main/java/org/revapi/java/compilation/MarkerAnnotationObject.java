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

package org.revapi.java.compilation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;

import javax.lang.model.element.NestingKind;
import javax.tools.SimpleJavaFileObject;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class MarkerAnnotationObject extends SimpleJavaFileObject {
    public static final String CLASS_NAME = "__RevapiMarkerAnnotation";

    private static final String SOURCE = "public @interface " + CLASS_NAME + " {}";

    public MarkerAnnotationObject() throws URISyntaxException {
        super(new URI(CLASS_NAME + ".java"), Kind.SOURCE);
    }

    @Override
    public NestingKind getNestingKind() {
        return NestingKind.TOP_LEVEL;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return new ByteArrayInputStream(SOURCE.getBytes());
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        return new StringReader(SOURCE);
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        return SOURCE;
    }
}
