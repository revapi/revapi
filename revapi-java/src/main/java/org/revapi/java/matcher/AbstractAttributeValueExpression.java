/*
 * Copyright 2014-2018 Lukas Krejci
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
package org.revapi.java.matcher;

import javax.lang.model.element.AnnotationValue;
import javax.lang.model.type.TypeMirror;

import org.revapi.Archive;
import org.revapi.ElementGateway;
import org.revapi.FilterMatch;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
abstract class AbstractAttributeValueExpression implements MatchExpression {
    @Override
    public final FilterMatch matches(ElementGateway.AnalysisStage stage, JavaModelElement element) {
        return FilterMatch.DOESNT_MATCH;
    }

    @Override
    public final FilterMatch matches(ElementGateway.AnalysisStage stage, JavaAnnotationElement annotation) {
        return FilterMatch.DOESNT_MATCH;
    }

    @Override
    public final FilterMatch matches(ElementGateway.AnalysisStage stage,
            AnnotationAttributeElement attribute) {
        return matches(stage, attribute.getAnnotationValue(), attribute.getArchive(), (ProbingEnvironment) attribute.getTypeEnvironment());
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, TypeParameterElement typeParameter) {
        return FilterMatch.DOESNT_MATCH;
    }

    @Override
    public final FilterMatch matches(ElementGateway.AnalysisStage stage, TypeMirror type) {
        return FilterMatch.DOESNT_MATCH;
    }

    public FilterMatch matches(ElementGateway.AnalysisStage stage, int index, AnnotationValue value,
            Archive archive, ProbingEnvironment env) {
        return matches(stage, value, archive, env);
    }

    public abstract FilterMatch matches(ElementGateway.AnalysisStage stage, AnnotationValue value, Archive archive,
            ProbingEnvironment env);
}
