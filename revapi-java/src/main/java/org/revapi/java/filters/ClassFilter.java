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
package org.revapi.java.filters;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.SimpleElementVisitor8;

import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.7.0
 */
public final class ClassFilter extends AbstractIncludeExcludeFilter {
    public ClassFilter() {
        super("revapi.java.filter.classes", "/META-INF/class-filter-schema.json");
    }

    @Override
    protected boolean canBeReIncluded(JavaModelElement element) {
        //an inner-class can be re-included, but not a method or a field
        return element instanceof JavaTypeElement;
    }

    @Override
    protected Stream<String> getTestedElementRepresentations(JavaModelElement element) {
        TypeElement type = getTypeOf(element.getModelElement());

        //include both the FQCN (which is always without type params) and the full name including the type parameters
        return Stream.of(type.getQualifiedName().toString(), Util.toHumanReadableString(type));
    }

    @Override
    protected void validateConfiguration(boolean excludes, List<String> fullMatches, List<Pattern> patterns,
            boolean regexes) {
        if (!regexes) {
            validateFullMatches(excludes, fullMatches);
        }
    }

    static void validateFullMatches(boolean excludes, List<String> fullMatches) {
        if (fullMatches.stream().filter(n -> !SourceVersion.isName(n)).findAny().isPresent()) {
            String message = excludes
                    ? "Excludes contain full matches on illegal Java names. This would" +
                    " effectively do nothing and is most probably a typo or misconfiguration on your side. If you" +
                    " intended to use regular expressions, you forgot to specify it."
                    : "Includes contain full matches on illegal Java names. This would" +
                    " effectively filter everything out and is most probably a typo or misconfiguration on your side." +
                    " If you intended to use regular expressions, you forgot to specify it.";

            throw new IllegalArgumentException(message);
        }
    }

    private TypeElement getTypeOf(Element element) {
        return element.accept(new SimpleElementVisitor8<TypeElement, Void>() {
            @Override
            public TypeElement visitVariable(VariableElement e, Void ignored) {
                return e.getEnclosingElement().accept(this, null);
            }

            @Override
            public TypeElement visitExecutable(ExecutableElement e, Void ignored) {
                return e.getEnclosingElement().accept(this, null);
            }

            @Override
            public TypeElement visitType(TypeElement e, Void ignored) {
                return e;
            }

            @Override
            public TypeElement visitTypeParameter(TypeParameterElement e, Void aVoid) {
                return e.getEnclosingElement().accept(this, null);
            }
        }, null);
    }
}
