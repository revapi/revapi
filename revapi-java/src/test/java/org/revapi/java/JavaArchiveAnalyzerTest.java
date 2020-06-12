/*
 * Copyright 2014-2020 Lukas Krejci
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
package org.revapi.java;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.revapi.API;
import org.revapi.Archive;
import org.revapi.Element;
import org.revapi.java.compilation.InclusionFilter;
import org.revapi.java.model.JavaElementForest;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.UseSite;


/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class JavaArchiveAnalyzerTest extends AbstractJavaElementAnalyzerTest {

    private static class ShrinkwrapArchive implements Archive {
        private final JavaArchive archive;

        private ShrinkwrapArchive(JavaArchive archive) {
            this.archive = archive;
        }

        @Nonnull
        @Override
        public String getName() {
            return archive.getName();
        }

        @Nonnull
        @Override
        public InputStream openStream() throws IOException {
            return archive.as(ZipExporter.class).exportAsInputStream();
        }
    }

    @Test
    public void testSimple() throws Exception {
        ArchiveAndCompilationPath archive = createCompiledJar("test.jar", "misc/A.java", "misc/B.java", "misc/C.java",
            "misc/D.java", "misc/I.java");

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(new API(
                Arrays.asList(new ShrinkwrapArchive(archive.archive)),
                null), emptyList(), Executors.newSingleThreadExecutor(), null, false,
                InclusionFilter.acceptAll());

        try {
            JavaElementForest forest = analyzer.analyze();

            Assert.assertEquals(6, forest.getRoots().size());
        } finally {
            deleteDir(archive.compilationPath);
            analyzer.getCompilationValve().removeCompiledResults();
        }
    }

    @Test
    public void testWithSupplementary() throws Exception {
        ArchiveAndCompilationPath compRes = createCompiledJar("a.jar", "v1/supplementary/a/A.java",
            "v1/supplementary/b/B.java", "v1/supplementary/a/C.java");

        JavaArchive api = ShrinkWrap.create(JavaArchive.class, "api.jar")
            .addAsResource(compRes.compilationPath.resolve("A.class").toFile(), "A.class");
        JavaArchive sup = ShrinkWrap.create(JavaArchive.class, "sup.jar")
            .addAsResource(compRes.compilationPath.resolve("B.class").toFile(), "B.class")
            .addAsResource(compRes.compilationPath.resolve("B$T$1.class").toFile(), "B$T$1.class")
            .addAsResource(compRes.compilationPath.resolve("B$T$1$TT$1.class").toFile(), "B$T$1$TT$1.class")
            .addAsResource(compRes.compilationPath.resolve("B$T$2.class").toFile(), "B$T$2.class")
            .addAsResource(compRes.compilationPath.resolve("C.class").toFile(), "C.class")
            .addAsResource(compRes.compilationPath.resolve("B$UsedByIgnoredClass.class").toFile(), "B$UsedByIgnoredClass.class")
            .addAsResource(compRes.compilationPath.resolve("A$PrivateEnum.class").toFile(), "A$PrivateEnum.class");

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(new API(Arrays.asList(new ShrinkwrapArchive(api)),
                Arrays.asList(new ShrinkwrapArchive(sup))), emptyList(), Executors.newSingleThreadExecutor(), null,
                false, InclusionFilter.acceptAll());

        try {
            JavaElementForest forest = analyzer.analyze();

            Assert.assertEquals(3, forest.getRoots().size());

            Iterator<TypeElement> roots = forest.getRoots().iterator();

            TypeElement A = roots.next();
            TypeElement B_T$1 = roots.next();
            TypeElement B_T$2 = roots.next();

            Assert.assertEquals("A", A.getCanonicalName());
            Assert.assertEquals("A", A.getDeclaringElement().getQualifiedName().toString());
            Assert.assertEquals("B.T$1", B_T$1.getCanonicalName());
            Assert.assertEquals("B.T$1", B_T$1.getDeclaringElement().getQualifiedName().toString());
            Assert.assertEquals("B.T$2", B_T$2.getCanonicalName());
            Assert.assertEquals("B.T$2", B_T$2.getDeclaringElement().getQualifiedName().toString());
        } finally {
            deleteDir(compRes.compilationPath);
            analyzer.getCompilationValve().removeCompiledResults();
        }
    }

    @Test
    public void testPreventRecursionWhenConstructingInheritedMembers() throws Exception {
        ArchiveAndCompilationPath archive = createCompiledJar("a.jar", "misc/MemberInheritsOwner.java");

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(new API(
                Arrays.asList(new ShrinkwrapArchive(archive.archive)),
                null), emptyList(), Executors.newSingleThreadExecutor(), null, false,
                InclusionFilter.acceptAll());

        try {
            JavaElementForest forest = analyzer.analyze();

            forest.getRoots();

            Assert.assertEquals(1, forest.getRoots().size());

            Predicate<Element> findMethod =
                    c -> "method void MemberInheritsOwner::method()".equals(c.getFullHumanReadableString());
            Predicate<Element> findMember1 =
                    c -> "interface MemberInheritsOwner.Member1".equals(c.getFullHumanReadableString());
            Predicate<Element> findMember2 =
                    c -> "interface MemberInheritsOwner.Member2".equals(c.getFullHumanReadableString());

            Element root = forest.getRoots().first();
            Assert.assertEquals(3 + 11, root.getChildren().size()); //11 is the number of methods on java.lang.Object
            Assert.assertTrue(root.getChildren().stream().anyMatch(findMethod));
            Assert.assertTrue(root.getChildren().stream().anyMatch(findMember1));
            Assert.assertTrue(root.getChildren().stream().anyMatch(findMember2));

            Assert.assertEquals(1 + 11, root.getChildren().stream().filter(findMember1).findFirst().get().getChildren().size());
            Assert.assertEquals(1 + 11, root.getChildren().stream().filter(findMember2).findFirst().get().getChildren().size());

        } finally {
            deleteDir(archive.compilationPath);
            analyzer.getCompilationValve().removeCompiledResults();
        }
    }

    @Test
    public void testTypeParametersDragIntoAPI() throws Exception {
        ArchiveAndCompilationPath compRes = createCompiledJar("a.jar", "misc/Generics.java",
                "misc/GenericsParams.java");

        JavaArchive api = ShrinkWrap.create(JavaArchive.class, "api.jar")
                .addAsResource(compRes.compilationPath.resolve("Generics.class").toFile(), "Generics.class");
        JavaArchive sup = ShrinkWrap.create(JavaArchive.class, "sup.jar")
                .addAsResource(compRes.compilationPath.resolve("GenericsParams.class").toFile(), "GenericsParams.class")
                .addAsResource(compRes.compilationPath.resolve("GenericsParams$TypeParam.class").toFile(), "GenericsParams$TypeParam.class")
                .addAsResource(compRes.compilationPath.resolve("GenericsParams$ExtendsBound.class").toFile(), "GenericsParams$ExtendsBound.class")
                .addAsResource(compRes.compilationPath.resolve("GenericsParams$SuperBound.class").toFile(), "GenericsParams$SuperBound.class")
                .addAsResource(compRes.compilationPath.resolve("GenericsParams$TypeVar.class").toFile(), "GenericsParams$TypeVar.class")
                .addAsResource(compRes.compilationPath.resolve("GenericsParams$TypeVarIface.class").toFile(), "GenericsParams$TypeVarIface.class")
                .addAsResource(compRes.compilationPath.resolve("GenericsParams$TypeVarImpl.class").toFile(), "GenericsParams$TypeVarImpl.class")
                .addAsResource(compRes.compilationPath.resolve("GenericsParams$Unused.class").toFile(), "GenericsParams$Unused.class");

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(new API(Arrays.asList(new ShrinkwrapArchive(api)),
                Arrays.asList(new ShrinkwrapArchive(sup))), emptyList(), Executors.newSingleThreadExecutor(), null,
                false, InclusionFilter.acceptAll());

        try {
            JavaElementForest forest = analyzer.analyze();

            Set<TypeElement> roots = forest.getRoots();

            Assert.assertEquals(7, roots.size());
            Assert.assertTrue(roots.stream().anyMatch(hasName("class Generics<T extends GenericsParams.TypeVar & GenericsParams.TypeVarIface, U extends Generics<GenericsParams.TypeVarImpl, ?>>")));
            Assert.assertTrue(roots.stream().anyMatch(hasName("class GenericsParams.ExtendsBound")));
            Assert.assertTrue(roots.stream().anyMatch(hasName("class GenericsParams.SuperBound")));
            Assert.assertTrue(roots.stream().anyMatch(hasName("class GenericsParams.TypeParam")));
            Assert.assertTrue(roots.stream().anyMatch(hasName("class GenericsParams.TypeVar")));
            Assert.assertTrue(roots.stream().anyMatch(hasName("interface GenericsParams.TypeVarIface")));
            Assert.assertTrue(roots.stream().anyMatch(hasName("class GenericsParams.TypeVarImpl")));
            Assert.assertFalse(roots.stream().anyMatch(hasName("class GenericsParams.Unused")));
        } finally {
            deleteDir(compRes.compilationPath);
            analyzer.getCompilationValve().removeCompiledResults();
        }
    }

    @Test
    public void testInheritedMembersResetArchiveToThatOfInheritingClass() throws Exception {
        ArchiveAndCompilationPath archive = createCompiledJar("c.jar", "misc/C.java");

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(new API(
                Arrays.asList(new ShrinkwrapArchive(archive.archive)),
                null), emptyList(), Executors.newSingleThreadExecutor(), null, false,
                InclusionFilter.acceptAll());

        try {
            JavaElementForest forest = analyzer.analyze();

            forest.getRoots();

            Assert.assertEquals(1, forest.getRoots().size());

            JavaTypeElement C = forest.getRoots().first();

            Assert.assertTrue(C.getChildren().stream().allMatch(e -> Objects.equals(((JavaElement) e).getArchive(),
                    C.getArchive())));
        } finally {
            deleteDir(archive.compilationPath);
            analyzer.getCompilationValve().removeCompiledResults();
        }
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    public void testInterfaceOverloadingObjectMethodUsesItsDeclaration() throws Exception {
        ArchiveAndCompilationPath archive = createCompiledJar("i.jar", "misc/InterfaceOverloadingObjectMethods.java");

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(new API(
                singletonList(new ShrinkwrapArchive(archive.archive)),
                null), emptyList(), Executors.newSingleThreadExecutor(), null, false,
                InclusionFilter.acceptAll());

        try {
            JavaElementForest forest = analyzer.analyze();

            forest.getRoots();

            Assert.assertEquals(1, forest.getRoots().size());

            JavaTypeElement I = forest.getRoots().first();

            Function<String, MethodElement> byName = name -> I.getChildren().stream()
                    .map(e -> (MethodElement) e)
                    .filter(m -> m.getDeclaringElement().getSimpleName().contentEquals(name))
                    .findFirst().get();

            MethodElement hashCode = byName.apply("hashCode");
            MethodElement toString = byName.apply("toString");
            MethodElement clone = byName.apply("clone");

            assertTrue(hashCode.isInherited());
            assertFalse(toString.isInherited());
            assertFalse(clone.isInherited());

            Set<UseSite> useSites = I.getUseSites();
            assertEquals(1, useSites.size());

            UseSite use = useSites.iterator().next();

            assertEquals(use.getUseType(), UseSite.Type.RETURN_TYPE);
            assertSame(clone, use.getSite());
        } finally {
            deleteDir(archive.compilationPath);
            analyzer.getCompilationValve().removeCompiledResults();
        }
    }

    private Predicate<TypeElement> hasName(String name) {
        return t -> name.equals(t.getFullHumanReadableString());
    }
}
