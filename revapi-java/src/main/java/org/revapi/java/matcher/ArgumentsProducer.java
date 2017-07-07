/*
 * Copyright 2015-2017 Lukas Krejci
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

package org.revapi.java.matcher;

import java.util.stream.Stream;

import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.JavaMethodParameterElement;
import org.revapi.query.Filter;

/**
 * @author Lukas Krejci
 */
final class ArgumentsProducer implements ChoiceProducer {
    private final Integer concreteIndex;

    public ArgumentsProducer(Integer concreteIndex) {
        this.concreteIndex = concreteIndex;
    }


    @Override
    public Stream<? extends JavaElement> choiceFor(JavaElement element) {
        if (!(element instanceof JavaMethodElement)) {
            return Stream.empty();
        } else if (concreteIndex != null) {
            return element.stream(JavaMethodParameterElement.class, false,
                    Filter.shallow(p -> p.getIndex() == concreteIndex - 1));
        } else {
            return element.stream(JavaMethodParameterElement.class, false, null);
        }
    }
}
