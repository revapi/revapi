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
package org.revapi.examples.elementmatcher;

import java.io.Reader;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.lang.model.element.ElementKind;

import org.revapi.AnalysisContext;
import org.revapi.ArchiveAnalyzer;
import org.revapi.Element;
import org.revapi.ElementMatcher;
import org.revapi.FilterMatch;
import org.revapi.FilterStartResult;
import org.revapi.TreeFilter;
import org.revapi.base.BaseElementMatcher;
import org.revapi.base.IndependentTreeFilter;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaTypeElement;

/**
 * {@link ElementMatcher}s are "helper" extensions that the other extensions may use to match the elements while
 * processing them. Usually, these would be the {@link org.revapi.TreeFilterProvider}s and
 * {@link org.revapi.DifferenceTransform}s but other extension types can use them, too.
 * <p>
 * The element matchers are available for the other extensions to pick from the `AnalysisContext` which they're
 * initialized with. It is not however defined how such extensions should pick the right element matcher. It could be
 * done for example by specifying the element matcher extension id (after all element matchers are extensions, too, and
 * therefore are configurable) but that is not prescribed.
 * <p>
 * It is assumed that the element matcher will be able to process some textual description of the elements it should
 * match - the recipe.
 * <p>
 * The element matcher "compiles" this recipe into a {@link TreeFilter} and this filter is then used for the actual
 * hierarchical matching during the API traversal.
 * <p>
 * Let's implement here a simple element matcher that will match any java type of certain kind.
 */
public class TypeKindElementMatcher extends BaseElementMatcher {

    @Nullable
    @Override
    public String getExtensionId() {
        return "type-kind";
    }

    @Override
    public void initialize(AnalysisContext analysisContext) {
        // we have no configuration, so no initialization is needed
    }

    @Override
    public Optional<CompiledRecipe> compile(String recipe) {
        ElementKind acceptedTypeKind = null;
        switch (recipe) {
        case "class":
            acceptedTypeKind = ElementKind.CLASS;
            break;
        case "interface":
            acceptedTypeKind = ElementKind.INTERFACE;
            break;
        case "@interface":
            acceptedTypeKind = ElementKind.ANNOTATION_TYPE;
            break;
        case "enum":
            acceptedTypeKind = ElementKind.ENUM;
            break;
        }

        if (acceptedTypeKind == null) {
            return Optional.empty();
        } else {
            ElementKind effectiveKind = acceptedTypeKind;
            return Optional.of(new CompiledRecipe() {
                @SuppressWarnings("unchecked")
                @Nullable
                @Override
                public <E extends Element<E>> TreeFilter<E> filterFor(ArchiveAnalyzer<E> archiveAnalyzer) {
                    if ("revapi.java".equals(archiveAnalyzer.getApiAnalyzer().getExtensionId())) {
                        // This is a little bit awkward part of the API where you need to make sure that you are safe
                        // to cast to the TreeFilter<E> yourself. We've done this by checking that we're dealing with
                        // the java API analyzer and therefore are dealing with the JavaElements so the cast below is
                        // safe.
                        return (TreeFilter<E>) new Filter(effectiveKind);
                    }
                    return null;
                }
            });
        }
    }

    /**
     * We extend the {@link IndependentTreeFilter} because this filter does not depend on anything else but the element
     * being matched.
     */
    private static final class Filter extends IndependentTreeFilter<JavaElement> {
        final ElementKind acceptedTypeKind;

        Filter(ElementKind acceptedTypeKind) {
            this.acceptedTypeKind = acceptedTypeKind;
        }

        @Override
        protected FilterStartResult doStart(JavaElement element) {
            if (!(element instanceof JavaTypeElement)) {
                // we are not able to filter anything other than java types. The matcher therefore needs to be
                // "transparent" and let other potential matchers do their filtering.
                return FilterStartResult.matchAndDescend();
            }

            JavaTypeElement type = (JavaTypeElement) element;

            boolean matches = type.getDeclaringElement().getKind() == acceptedTypeKind;

            return FilterStartResult.direct(FilterMatch.fromBoolean(matches), false);
        }
    }
}
