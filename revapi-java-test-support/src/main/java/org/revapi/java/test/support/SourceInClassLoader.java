/*
 * Copyright 2014-2017 Lukas Krejci
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
package org.revapi.java.test.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;

import javax.lang.model.element.NestingKind;
import javax.tools.SimpleJavaFileObject;

final class SourceInClassLoader extends SimpleJavaFileObject {
    private final URL url;

    public SourceInClassLoader(URI path, URI pathInClassloader) {
        super(path, Kind.SOURCE);
        url = getClass().getResource(pathInClassloader.getPath());
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

    @Override
    public NestingKind getNestingKind() {
        return NestingKind.TOP_LEVEL;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return url.openStream();
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        return new InputStreamReader(openInputStream(), "UTF-8");
    }
}
