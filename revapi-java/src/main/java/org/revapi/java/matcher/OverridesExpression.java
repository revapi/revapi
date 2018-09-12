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

import java.util.Iterator;
import java.util.List;

import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import org.revapi.ElementGateway;
import org.revapi.FilterMatch;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.TypeEnvironment;
import org.revapi.java.spi.Util;
import org.revapi.query.Filter;

/**
 * @author Lukas Krejci
 */
final class OverridesExpression implements MatchExpression {
    private final MatchExpression overriddenMethodMatch;

    OverridesExpression(MatchExpression overriddenMethodMatch) {
        this.overriddenMethodMatch = overriddenMethodMatch;
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, JavaModelElement element) {
        if (!(element instanceof JavaMethodElement)) {
            return FilterMatch.DOESNT_MATCH;
        }

        JavaMethodElement overridingMethodElement = (JavaMethodElement) element;
        ExecutableType overridingMethodType = overridingMethodElement.getModelRepresentation();

        TypeEnvironment typeEnvironment = element.getTypeEnvironment();
        Types types = typeEnvironment.getTypeUtils();

        Filter<JavaMethodElement> check = Filter.shallow(m -> {
            boolean ret = overridingMethodElement.getDeclaringElement().getSimpleName()
                    .contentEquals(m.getDeclaringElement().getSimpleName());
            ret = ret && types.isSubsignature(overridingMethodType, m.getModelRepresentation());
            return ret;
        });

        List<TypeMirror> parentTypes = Util.getAllSuperTypes(types, overridingMethodElement.getParent().getModelRepresentation());

        FilterMatch ret = FilterMatch.DOESNT_MATCH;

        while (!parentTypes.isEmpty()) {
            TypeMirror pt = parentTypes.remove(0);

            JavaTypeElement parentType = typeEnvironment.getModelElement(pt);
            if (parentType == null) {
                continue;
            }

            Iterator<JavaMethodElement> ms = parentType.iterateOverChildren(JavaMethodElement.class, false, check);
            if (overriddenMethodMatch == null) {
                ret = ret.or(FilterMatch.fromBoolean(ms.hasNext()));
                if (ret == FilterMatch.MATCHES) {
                    return ret;
                }
            } else {
                while (ms.hasNext()) {
                    JavaMethodElement m = ms.next();

                    ret = ret.or(overriddenMethodMatch.matches(stage, m));

                    if (ret == FilterMatch.MATCHES) {
                        return ret;
                    }
                }
            }

            Util.fillAllSuperTypes(types, pt, parentTypes);
        }

        return ret;
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, JavaAnnotationElement annotation) {
        return FilterMatch.DOESNT_MATCH;
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, AnnotationAttributeElement attribute) {
        return FilterMatch.DOESNT_MATCH;
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, TypeParameterElement typeParameter) {
        return FilterMatch.DOESNT_MATCH;
    }
}
