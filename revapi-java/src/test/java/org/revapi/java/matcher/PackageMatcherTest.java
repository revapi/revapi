/*
 * Copyright 2014-2022 Lukas Krejci
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;

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
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.TypeEnvironment;

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

        TypeEnvironment env = mock(TypeEnvironment.class);
        Elements els = mock(Elements.class);

        when(env.getElementUtils()).thenReturn(els);

        JavaTypeElement typeEl = mock(JavaTypeElement.class);
        when(typeEl.getDeclaringElement()).thenReturn(type);
        when(typeEl.getTypeEnvironment()).thenReturn(env);

        when(els.getPackageOf(eq(type))).thenReturn(pkg);

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

        TypeEnvironment env = mock(TypeEnvironment.class);
        Elements els = mock(Elements.class);

        when(env.getElementUtils()).thenReturn(els);

        JavaTypeElement typeEl = mock(JavaTypeElement.class);
        when(typeEl.getDeclaringElement()).thenReturn(type);
        when(typeEl.getTypeEnvironment()).thenReturn(env);

        when(els.getPackageOf(eq(type))).thenReturn(pkg);

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
    void testInheritedInnerClass() {
        PackageMatcher matcher = new PackageMatcher();
        ElementMatcher.CompiledRecipe recipe = matcher.compile("com.acme").get();
        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(new JavaApiAnalyzer(), null, null, null, null, false,
                null);

        TypeEnvironment env = mock(TypeEnvironment.class);
        Elements els = mock(Elements.class);
        when(env.getElementUtils()).thenReturn(els);

        PackageElement basePackage = mock(PackageElement.class);
        when(basePackage.getQualifiedName()).thenReturn(new StringName("com.base"));
        PackageElement inheritorPackage = mock(PackageElement.class);
        when(inheritorPackage.getQualifiedName()).thenReturn(new StringName("com.acme"));

        TypeElement baseType = mock(TypeElement.class);
        DeclaredType baseTypeMirror = mock(DeclaredType.class);
        JavaTypeElement baseTypeEl = mock(JavaTypeElement.class);
        when(baseTypeEl.getTypeEnvironment()).thenReturn(env);
        when(baseTypeEl.getDeclaringElement()).thenReturn(baseType);
        when(els.getPackageOf(eq(baseType))).thenReturn(basePackage);

        TypeElement innerType = mock(TypeElement.class);
        JavaTypeElement innerTypeEl = mock(JavaTypeElement.class);
        when(innerTypeEl.getTypeEnvironment()).thenReturn(env);
        when(innerTypeEl.getDeclaringElement()).thenReturn(innerType);
        when(els.getPackageOf(eq(innerType))).thenReturn(basePackage);

        TypeElement inheritorType = mock(TypeElement.class);
        JavaTypeElement inheritorTypeEl = mock(JavaTypeElement.class);
        when(inheritorTypeEl.getTypeEnvironment()).thenReturn(env);
        when(inheritorTypeEl.getDeclaringElement()).thenReturn(inheritorType);
        when(els.getPackageOf(eq(inheritorType))).thenReturn(inheritorPackage);

        // the inherited element behavior
        when(innerType.getEnclosingElement()).thenReturn(baseType);
        when(innerTypeEl.getParent()).thenReturn(inheritorTypeEl);
        when(inheritorType.getSuperclass()).thenReturn(baseTypeMirror);

        TreeFilter<JavaElement> filter = recipe.filterFor(analyzer);
        assertNotNull(filter);

        FilterStartResult res = filter.start(innerTypeEl);
        assertEquals(Ternary.TRUE, res.getMatch());
        assertEquals(Ternary.TRUE, res.getDescend());
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
