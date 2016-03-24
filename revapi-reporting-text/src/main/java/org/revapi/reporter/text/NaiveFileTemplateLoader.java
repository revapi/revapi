/*
 * Copyright 2016 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 *
 */

package org.revapi.reporter.text;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import freemarker.cache.TemplateLoader;

/**
 * A naive file template loader for FreeMarker that doesn't impose any security restrictions as the FreeMarker's own
 * {@link freemarker.cache.FileTemplateLoader} does.
 *
 * @author Lukas Krejci
 * @since 0.5.0
 */
public class NaiveFileTemplateLoader implements TemplateLoader {
    @Override
    public Object findTemplateSource(String name) throws IOException {
        File ret = new File(name);
        return ret.exists() ? ret : null;
    }

    @Override
    public long getLastModified(Object templateSource) {
        return ((File) templateSource).lastModified();
    }

    @Override
    public Reader getReader(Object templateSource, String encoding) throws IOException {
        return new InputStreamReader(new FileInputStream((File) templateSource), encoding);
    }

    @Override
    public void closeTemplateSource(Object templateSource) throws IOException {
    }
}
