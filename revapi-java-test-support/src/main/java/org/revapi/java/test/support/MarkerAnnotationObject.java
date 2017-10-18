package org.revapi.java.test.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;

import javax.lang.model.element.NestingKind;
import javax.tools.SimpleJavaFileObject;

final class MarkerAnnotationObject extends SimpleJavaFileObject {
    public static final String CLASS_NAME = "__RevapiMarkerAnnotation";

    private static final String SOURCE = "public @interface " + CLASS_NAME + " {}";

    public MarkerAnnotationObject() {
        super(URI.create(CLASS_NAME + ".java"), Kind.SOURCE);
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
