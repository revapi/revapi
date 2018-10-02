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

import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.revapi.AnalysisContext;
import org.revapi.ArchiveAnalyzer;
import org.revapi.Element;
import org.revapi.ElementMatcher;
import org.revapi.FilterMatch;
import org.revapi.FilterProvider;
import org.revapi.FilterResult;
import org.revapi.TreeFilter;
import org.revapi.classif.Classif;
import org.revapi.classif.MatchingProgress;
import org.revapi.classif.ModelInspector;
import org.revapi.classif.StructuralMatcher;
import org.revapi.classif.TestResult;
import org.revapi.classif.WalkInstruction;
import org.revapi.java.JavaArchiveAnalyzer;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.model.JavaElementBase;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
public final class JavaElementMatcher implements ElementMatcher {
    @Override
    public Optional<CompiledRecipe> compile(String recipe) {
        try {
            StructuralMatcher matcher = Classif.compile(recipe);

            return Optional.of(archiveAnalyzer -> {
                if (!(archiveAnalyzer instanceof JavaArchiveAnalyzer)) {
                    return null;
                }

                MatchingProgress<Element> progress = matcher
                        .with(new ElementInspector((JavaArchiveAnalyzer) archiveAnalyzer));

                return new TreeFilter() {
                    @Override
                    public FilterResult start(Element element) {
                        return convert(progress.start(element));
                    }

                    @Override
                    public FilterMatch finish(Element element) {
                        return convert(progress.finish(element));
                    }

                    @Override
                    public Map<Element, FilterMatch> finish() {
                        return progress.finish().entrySet().stream()
                                .collect(Collectors.toMap(Map.Entry::getKey, e -> convert(e.getValue())));
                    }
                };
            });
        } catch (IllegalArgumentException __) {
            return Optional.empty();
        }
    }

    @Override
    public void close() throws Exception {
    }

    @Override
    public String getExtensionId() {
        return "matcher.java";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
    }

    private static FilterResult convert(WalkInstruction instruction) {
        return FilterResult.from(convert(instruction.getTestResult()), instruction.isDescend());
    }

    private static FilterMatch convert(TestResult result) {
        switch (result) {
            case DEFERRED:
                return FilterMatch.UNDECIDED;
            case PASSED:
                return FilterMatch.MATCHES;
            case NOT_PASSED:
                return FilterMatch.DOESNT_MATCH;
            default:
                throw new IllegalArgumentException(result + " not handled.");
        }
    }

    private static JavaModelElement toJava(Element element) {
        if (!(element instanceof JavaModelElement)) {
            throw new IllegalArgumentException("Only instances of JavaModelElement can be processed by matcher.java");
        }

        return (JavaModelElement) element;
    }

    private static final class ElementInspector implements ModelInspector<Element> {
        final Elements elements;
        final Types types;
        final TypeElement javaLangObject;
        final ProbingEnvironment env;

        ElementInspector(JavaArchiveAnalyzer analyzer) {
            env = analyzer.getProbingEnvironment();
            elements = env.getElementUtils();
            types = env.getTypeUtils();
            javaLangObject = elements.getTypeElement("java.lang.Object");
        }

        @Override
        public TypeElement getJavaLangObjectElement() {
            return javaLangObject;
        }

        @Override
        public javax.lang.model.element.Element toElement(Element element) {
            return toJava(element).getDeclaringElement();
        }

        @Override
        public TypeMirror toMirror(Element element) {
            return toJava(element).getModelRepresentation();
        }

        @Override
        public Set<Element> getUses(Element element) {
            //TODO implement
            return null;
        }

        @Override
        public Set<Element> getUseSites(Element element) {
            //TODO implement
            return null;
        }

        @Override
        public Element fromElement(javax.lang.model.element.Element element) {
            //TODO implement
            return null;
        }

        @Override
        public List<? extends TypeMirror> directSupertypes(TypeMirror typeMirror) {
            return types.directSupertypes(typeMirror);
        }

        @Override
        public boolean overrides(ExecutableElement overrider, ExecutableElement overriden, TypeElement type) {
            return elements.overrides(overrider, overriden, type);
        }
    }
}
