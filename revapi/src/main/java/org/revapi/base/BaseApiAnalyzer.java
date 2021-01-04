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

import org.revapi.ApiAnalyzer;
import org.revapi.CorrespondenceComparatorDeducer;
import org.revapi.Element;

/**
 * A convenience base class for API analyzers.
 *
 * @param <E> the parent type of all elements produced by this API analyzer
 */
public abstract class BaseApiAnalyzer<E extends Element<E>> extends BaseConfigurable implements ApiAnalyzer<E> {
    @Override
    public CorrespondenceComparatorDeducer<E> getCorrespondenceDeducer() {
        return CorrespondenceComparatorDeducer.naturalOrder();
    }

    @Override
    public void close() throws Exception {
    }
}
