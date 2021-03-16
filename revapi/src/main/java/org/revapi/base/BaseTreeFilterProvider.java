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

import java.util.Optional;

import org.revapi.ArchiveAnalyzer;
import org.revapi.Element;
import org.revapi.TreeFilter;
import org.revapi.TreeFilterProvider;

/**
 * A convenience base class for tree filter providers.
 */
public abstract class BaseTreeFilterProvider extends BaseConfigurable implements TreeFilterProvider {
    @Override
    public <E extends Element<E>> Optional<TreeFilter<E>> filterFor(ArchiveAnalyzer<E> archiveAnalyzer) {
        return Optional.empty();
    }

    @Override
    public void close() throws Exception {

    }
}
