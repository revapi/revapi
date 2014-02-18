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

package org.revapi;

import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Configuration {

    private final Locale locale;
    private final Map<String, String> properties;
    private final API oldApi;
    private final API newApi;

    public Configuration(@Nonnull Locale locale, @Nonnull Map<String, String> properties, @Nonnull API oldApi,
        @Nonnull API newApi) {
        this.locale = locale;
        this.properties = properties;
        this.oldApi = oldApi;
        this.newApi = newApi;
    }

    public
    @Nonnull
    Locale getLocale() {
        return locale;
    }

    public
    @Nonnull
    Map<String, String> getProperties() {
        return properties;
    }

    public
    @Nonnull
    API getOldApi() {
        return oldApi;
    }

    public
    @Nonnull
    API getNewApi() {
        return newApi;
    }
}
