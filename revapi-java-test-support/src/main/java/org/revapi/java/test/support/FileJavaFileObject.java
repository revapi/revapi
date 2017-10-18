package org.revapi.java.test.support;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;

import javax.tools.SimpleJavaFileObject;

final class FileJavaFileObject extends SimpleJavaFileObject {
    private final File file;

    public FileJavaFileObject(URI uri, File file) {
        super(uri, Kind.SOURCE);
        this.file = file;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        return new InputStreamReader(openInputStream(), "UTF-8");
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        StringBuilder bld = new StringBuilder();

        Reader rdr = openReader(ignoreEncodingErrors);
        char[] buffer = new char[512]; //our source files are small

        for (int cnt; (cnt = rdr.read(buffer)) != -1; ) {
            bld.append(buffer, 0, cnt);
        }

        rdr.close();

        return bld;
    }
}
