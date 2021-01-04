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
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import org.revapi.Archive;

/**
 * A simple implementation of the {@link Archive} interface that uses a "factory" supplier that is able to return a new
 * input stream representing the archive every time it is called.
 */
public class InputStreamArchive implements Archive {
    private final String name;
    private final Supplier<InputStream> dataSupplier;

    public InputStreamArchive(String name, Supplier<InputStream> dataSupplier) {
        this.name = name;
        this.dataSupplier = dataSupplier;
    }

    @Nonnull
    @Override
    public String getName() {
        return name;
    }

    @Nonnull
    @Override
    public InputStream openStream() throws IOException {
        return dataSupplier.get();
    }
}
