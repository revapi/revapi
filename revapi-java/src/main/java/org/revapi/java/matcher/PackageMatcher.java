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

import java.util.Optional;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.lang.model.element.PackageElement;

import org.revapi.ArchiveAnalyzer;
import org.revapi.Element;
import org.revapi.FilterStartResult;
import org.revapi.Ternary;
import org.revapi.TreeFilter;
import org.revapi.base.BaseElementMatcher;
import org.revapi.base.IndependentTreeFilter;
import org.revapi.java.JavaArchiveAnalyzer;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;

public class PackageMatcher extends BaseElementMatcher {
    @Override
    public Optional<CompiledRecipe> compile(String recipe) {
        return Optional.of(new CompiledRecipe() {
            final Pattern pattern = recipe.length() > 1 && recipe.startsWith("/") && recipe.endsWith("/")
                    ? Pattern.compile(recipe.substring(1, recipe.length() - 1))
                    : null;

            @SuppressWarnings("unchecked")
            @Nullable
            @Override
            public <E extends Element<E>> TreeFilter<E> filterFor(ArchiveAnalyzer<E> archiveAnalyzer) {
                if (!(archiveAnalyzer instanceof JavaArchiveAnalyzer)) {
                    return null;
                }

                return (TreeFilter<E>) new IndependentTreeFilter<JavaElement>() {
                    @Override
                    protected FilterStartResult doStart(JavaElement element) {
                        if (!(element instanceof JavaModelElement)) {
                            return FilterStartResult.defaultResult();
                        }

                        JavaModelElement modelElement = (JavaModelElement) element;

                        PackageElement pkg = getPackage(modelElement.getDeclaringElement());

                        Ternary ret = Ternary.fromBoolean(matches(pkg));

                        return FilterStartResult.direct(ret, ret);
                    }

                    private PackageElement getPackage(javax.lang.model.element.Element el) {
                        while (!(el instanceof PackageElement)) {
                            el = el.getEnclosingElement();
                        }

                        return (PackageElement) el;
                    }

                    private boolean matches(PackageElement pkg) {
                        if (pattern == null) {
                            return recipe.contentEquals(pkg.getQualifiedName());
                        } else {
                            return pattern.matcher(pkg.getQualifiedName()).matches();
                        }
                    }
                };
            }
        });
    }

    @Override
    public String getExtensionId() {
        return "java-package";
    }
}
