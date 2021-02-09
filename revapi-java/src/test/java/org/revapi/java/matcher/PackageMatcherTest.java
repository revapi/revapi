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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import org.junit.jupiter.api.Test;
import org.revapi.API;
import org.revapi.ApiAnalyzer;
import org.revapi.ArchiveAnalyzer;
import org.revapi.DifferenceAnalyzer;
import org.revapi.ElementMatcher;
import org.revapi.FilterFinishResult;
import org.revapi.FilterStartResult;
import org.revapi.Ternary;
import org.revapi.TreeFilter;
import org.revapi.base.BaseApiAnalyzer;
import org.revapi.base.BaseArchiveAnalyzer;
import org.revapi.java.AbstractJavaElementAnalyzerTest;
import org.revapi.java.JavaApiAnalyzer;
import org.revapi.java.JavaArchiveAnalyzer;
import org.revapi.java.model.JavaElementForest;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.JavaTypeElement;

class PackageMatcherTest extends AbstractJavaElementAnalyzerTest {

    @Test
    void testMatchesExact() {
        String pkgName = "com.acme";
        PackageMatcher matcher = new PackageMatcher();
        ElementMatcher.CompiledRecipe recipe = matcher.compile(pkgName).get();

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(new JavaApiAnalyzer(), null, null, null, null, false,
                null);

        TypeElement type = mock(TypeElement.class);
        PackageElement pkg = mock(PackageElement.class);

        JavaTypeElement typeEl = mock(JavaTypeElement.class);
        when(typeEl.getDeclaringElement()).thenReturn(type);
        when(type.getEnclosingElement()).thenReturn(pkg);

        when(pkg.getQualifiedName()).thenReturn(new StringName(pkgName));

        TreeFilter<JavaElement> filter = recipe.filterFor(analyzer);
        assertNotNull(filter);

        FilterStartResult sRes = filter.start(typeEl);
        assertSame(Ternary.TRUE, sRes.getMatch());
        assertSame(Ternary.TRUE, sRes.getDescend());

        FilterFinishResult fRes = filter.finish(typeEl);
        assertSame(Ternary.TRUE, fRes.getMatch());
        assertFalse(fRes.isInherited());

        when(pkg.getQualifiedName()).thenReturn(new StringName(pkgName + "no"));

        sRes = filter.start(typeEl);
        assertSame(Ternary.FALSE, sRes.getMatch());
        assertSame(Ternary.FALSE, sRes.getDescend());

        fRes = filter.finish(typeEl);
        assertSame(Ternary.FALSE, fRes.getMatch());
        assertFalse(fRes.isInherited());
    }

    @Test
    void testMatchesByRegex() {
        String pkgName = "com.acme";
        PackageMatcher matcher = new PackageMatcher();
        ElementMatcher.CompiledRecipe recipe = matcher.compile("/com\\.a.me/").get();

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(new JavaApiAnalyzer(), null, null, null, null, false,
                null);

        TypeElement type = mock(TypeElement.class);
        PackageElement pkg = mock(PackageElement.class);

        JavaTypeElement typeEl = mock(JavaTypeElement.class);
        when(typeEl.getDeclaringElement()).thenReturn(type);
        when(type.getEnclosingElement()).thenReturn(pkg);

        when(pkg.getQualifiedName()).thenReturn(new StringName(pkgName));

        TreeFilter<JavaElement> filter = recipe.filterFor(analyzer);
        assertNotNull(filter);

        FilterStartResult sRes = filter.start(typeEl);
        assertSame(Ternary.TRUE, sRes.getMatch());
        assertSame(Ternary.TRUE, sRes.getDescend());

        FilterFinishResult fRes = filter.finish(typeEl);
        assertSame(Ternary.TRUE, fRes.getMatch());
        assertFalse(fRes.isInherited());

        when(pkg.getQualifiedName()).thenReturn(new StringName(pkgName + "no"));

        sRes = filter.start(typeEl);
        assertSame(Ternary.FALSE, sRes.getMatch());
        assertSame(Ternary.FALSE, sRes.getDescend());

        fRes = filter.finish(typeEl);
        assertSame(Ternary.FALSE, fRes.getMatch());
        assertFalse(fRes.isInherited());
    }

    @Test
    void testMatchesMethod() {
        String pkgName = "com.acme";
        PackageMatcher matcher = new PackageMatcher();
        ElementMatcher.CompiledRecipe recipe = matcher.compile(pkgName).get();

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(new JavaApiAnalyzer(), null, null, null, null, false,
                null);

        TypeElement type = mock(TypeElement.class);
        PackageElement pkg = mock(PackageElement.class);
        ExecutableElement method = mock(ExecutableElement.class);

        JavaMethodElement methodEl = mock(JavaMethodElement.class);
        when(methodEl.getDeclaringElement()).thenReturn(method);
        when(method.getEnclosingElement()).thenReturn(type);
        when(type.getEnclosingElement()).thenReturn(pkg);

        when(pkg.getQualifiedName()).thenReturn(new StringName(pkgName));

        TreeFilter<JavaElement> filter = recipe.filterFor(analyzer);
        assertNotNull(filter);

        FilterStartResult sRes = filter.start(methodEl);
        assertSame(Ternary.TRUE, sRes.getMatch());
        assertSame(Ternary.TRUE, sRes.getDescend());

        FilterFinishResult fRes = filter.finish(methodEl);
        assertSame(Ternary.TRUE, fRes.getMatch());
        assertFalse(fRes.isInherited());

        when(pkg.getQualifiedName()).thenReturn(new StringName(pkgName + "no"));

        sRes = filter.start(methodEl);
        assertSame(Ternary.FALSE, sRes.getMatch());
        assertSame(Ternary.FALSE, sRes.getDescend());

        fRes = filter.finish(methodEl);
        assertSame(Ternary.FALSE, fRes.getMatch());
        assertFalse(fRes.isInherited());
    }

    @Test
    void testIgnoresNonJavaAnalyzers() {
        PackageMatcher matcher = new PackageMatcher();
        ElementMatcher.CompiledRecipe recipe = matcher.compile("com.acme").get();

        ApiAnalyzer<JavaElement> apiAnalyzer = new BaseApiAnalyzer<JavaElement>() {
            @Override
            public ArchiveAnalyzer<JavaElement> getArchiveAnalyzer(API api) {
                return new BaseArchiveAnalyzer<JavaElementForest, JavaElement>(this, api) {
                    @Override
                    protected JavaElementForest newElementForest() {
                        return null;
                    }

                    @Override
                    protected Stream<JavaElement> discoverRoots(@Nullable Object context) {
                        return null;
                    }

                    @Override
                    protected Stream<JavaElement> discoverElements(@Nullable Object context, JavaElement parent) {
                        return null;
                    }
                };
            }

            @Override
            public DifferenceAnalyzer<JavaElement> getDifferenceAnalyzer(ArchiveAnalyzer<JavaElement> oldArchive,
                    ArchiveAnalyzer<JavaElement> newArchive) {
                return null;
            }

            @Override
            public String getExtensionId() {
                return null;
            }
        };

        ArchiveAnalyzer<JavaElement> archiveAnalyzer = apiAnalyzer.getArchiveAnalyzer(null);

        assertNull(recipe.filterFor(archiveAnalyzer));
    }

    private static final class StringName implements Name {
        final String value;

        private StringName(String value) {
            this.value = value;
        }

        @Override
        public boolean contentEquals(CharSequence cs) {
            return value.contentEquals(cs);
        }

        @Override
        public int length() {
            return value.length();
        }

        @Override
        public char charAt(int index) {
            return value.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return value.subSequence(start, end);
        }

        @Override
        @SuppressWarnings("NullableProblems")
        public String toString() {
            return value;
        }
    }
}
