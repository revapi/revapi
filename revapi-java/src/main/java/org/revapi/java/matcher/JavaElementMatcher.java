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
package org.revapi.java.matcher;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
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
import org.revapi.FilterFinishResult;
import org.revapi.FilterStartResult;
import org.revapi.Reference;
import org.revapi.Ternary;
import org.revapi.TreeFilter;
import org.revapi.classif.ModelInspector;
import org.revapi.classif.StructuralMatcher;
import org.revapi.classif.TestResult;
import org.revapi.classif.dsl.ClassifDSL;
import org.revapi.classif.progress.MatchingProgress;
import org.revapi.classif.progress.WalkInstruction;
import org.revapi.java.JavaArchiveAnalyzer;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.model.JavaElementFactory;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.UseSite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukas Krejci
 */
public final class JavaElementMatcher implements ElementMatcher {
    private static final Logger LOG = LoggerFactory.getLogger(JavaElementMatcher.class);

    @Override
    public Optional<CompiledRecipe> compile(String recipe) {
        try {
            StructuralMatcher matcher = ClassifDSL.compile(recipe);

            CompiledRecipe ret = new CompiledRecipe() {
                @SuppressWarnings("unchecked")
                @Nullable
                @Override
                public <E extends Element<E>> TreeFilter<E> filterFor(ArchiveAnalyzer<E> archiveAnalyzer) {
                    if (!(archiveAnalyzer instanceof JavaArchiveAnalyzer)) {
                        return null;
                    }

                    MatchingProgress<JavaElement> progress = matcher
                            .with(new ElementInspector((JavaArchiveAnalyzer) archiveAnalyzer));

                    TreeFilter<JavaElement> ret = new TreeFilter<JavaElement>() {
                        @Override
                        public FilterStartResult start(JavaElement element) {
                            if (!(element instanceof JavaModelElement)) {
                                return FilterStartResult.doesntMatch();
                            }
                            return convert(progress.start(element));
                        }

                        @Override
                        public FilterFinishResult finish(JavaElement element) {
                            if (!(element instanceof JavaModelElement)) {
                                return FilterFinishResult.doesntMatch();
                            }
                            return FilterFinishResult.direct(convert(progress.finish(element)));
                        }

                        @Override
                        public Map<JavaElement, FilterFinishResult> finish() {
                            return progress.finish().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                                    e -> FilterFinishResult.direct(convert(e.getValue()))));
                        }
                    };

                    // this is a safe cast because we're checking that the provided analyzer is indeed a java archive
                    // analyzer...
                    return (TreeFilter<E>) ret;
                }
            };

            return Optional.of(ret);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public void close() throws Exception {
    }

    @Override
    public String getExtensionId() {
        return "java";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
    }

    private static FilterStartResult convert(WalkInstruction instruction) {
        return FilterStartResult.direct(convert(instruction.getTestResult()),
                Ternary.fromBoolean(instruction.isDescend()));
    }

    private static Ternary convert(TestResult result) {
        switch (result) {
        case DEFERRED:
            return Ternary.UNDECIDED;
        case PASSED:
            return Ternary.TRUE;
        case NOT_PASSED:
            return Ternary.FALSE;
        default:
            throw new IllegalArgumentException(result + " not handled.");
        }
    }

    private static JavaModelElement toJava(JavaElement element) {
        if (!(element instanceof JavaModelElement)) {
            throw new IllegalArgumentException("Only instances of JavaModelElement can be processed by matcher.java");
        }

        return (JavaModelElement) element;
    }

    private static final class ElementInspector implements ModelInspector<JavaElement> {
        private Elements elements;
        private Types types;
        private TypeElement javaLangObject;
        final ProbingEnvironment env;

        ElementInspector(JavaArchiveAnalyzer analyzer) {
            env = analyzer.getProbingEnvironment();
        }

        @Override
        public TypeElement getJavaLangObjectElement() {
            return getJavaLangObject();
        }

        @Override
        public javax.lang.model.element.Element toElement(JavaElement element) {
            return toJava(element).getDeclaringElement();
        }

        @Override
        public TypeMirror toMirror(JavaElement element) {
            return toJava(element).getModelRepresentation();
        }

        @Override
        public Set<JavaElement> getUses(JavaElement element) {
            if (!env.isScanningComplete()) {
                return null;
            } else {
                return element.getReferencedElements().stream().map(Reference::getElement).collect(toSet());
            }
        }

        @Override
        public Set<JavaElement> getUseSites(JavaElement element) {
            if (!env.isScanningComplete()) {
                return null;
            } else {
                return element.getReferencingElements().stream().map(Reference::getElement).collect(toSet());
            }
        }

        @Override
        public JavaElement fromElement(javax.lang.model.element.Element element) {
            if (!env.isScanningComplete()) {
                return JavaElementFactory.elementFor(element, element.asType(), env, null);
            } else {
                List<javax.lang.model.element.Element> path = new ArrayList<>(3);
                javax.lang.model.element.Element current = element;
                while (current != null && !(current instanceof TypeElement)) {
                    path.add(current);
                    current = current.getEnclosingElement();
                }

                TypeElement type = (TypeElement) current;
                JavaModelElement model = env.getTypeMap().get(type);
                ListIterator<javax.lang.model.element.Element> it = path.listIterator(path.size());
                while (it.hasPrevious()) {
                    javax.lang.model.element.Element child = it.previous();
                    boolean found = false;
                    for (Element e : model.getChildren()) {
                        if (!(e instanceof JavaModelElement)) {
                            // annotations
                            continue;
                        }

                        JavaModelElement m = (JavaModelElement) e;
                        if (m.getDeclaringElement() == child) {
                            model = m;
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        return null;
                    }
                }

                return model;
            }
        }

        @Override
        public List<? extends TypeMirror> directSupertypes(TypeMirror typeMirror) {
            return getTypes().directSupertypes(typeMirror);
        }

        @Override
        public boolean overrides(ExecutableElement overrider, ExecutableElement overriden, TypeElement type) {
            return getElements().overrides(overrider, overriden, type);
        }

        public Elements getElements() {
            if (elements == null) {
                elements = env.getElementUtils();
            }
            return elements;
        }

        public Types getTypes() {
            if (types == null) {
                types = env.getTypeUtils();
            }
            return types;
        }

        public TypeElement getJavaLangObject() {
            if (javaLangObject == null) {
                javaLangObject = getElements().getTypeElement("java.lang.Object");
            }
            return javaLangObject;
        }
    }
}
