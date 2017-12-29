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
package org.revapi.simple;

import org.revapi.Element;
import org.revapi.ElementGateway;
import org.revapi.FilterResult;

public class SimpleElementGateway extends SimpleConfigurable implements ElementGateway {
    @Override
    public void start(AnalysisStage stage) {

    }

    @Override
    public FilterResult filter(AnalysisStage stage, Element element) {
        return FilterResult.doesntMatch();
    }

    @Override
    public void end(AnalysisStage stage) {

    }
}
