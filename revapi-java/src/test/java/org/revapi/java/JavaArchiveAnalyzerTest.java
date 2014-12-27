/*
 * Copyright 2014 Lukas Krejci
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
 */

package org.revapi.java;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.Executors;

import javax.annotation.Nonnull;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.API;
import org.revapi.Archive;
import org.revapi.java.model.JavaElementForest;
import org.revapi.java.model.TypeElement;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;


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

        try {
            JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(new API(
                Arrays.asList(new ShrinkwrapArchive(archive.archive)),
                null), Executors.newSingleThreadExecutor(), null, false, Collections.<File>emptySet(), false);

            JavaElementForest forest = analyzer.analyze();

            Assert.assertEquals(6, forest.getRoots().size());
        } finally {
            deleteDir(archive.compilationPath);
        }
    }

    @Test
    public void testWithSupplementary() throws Exception {
        ArchiveAndCompilationPath compRes = createCompiledJar("a.jar", "v1/supplementary/a/A.java",
            "v1/supplementary/b/B.java", "v1/supplementary/b/C.java");

        JavaArchive api = ShrinkWrap.create(JavaArchive.class, "api.jar")
            .addAsResource(compRes.compilationPath.resolve("A.class").toFile(), "A.class");
        JavaArchive sup = ShrinkWrap.create(JavaArchive.class, "sup.jar")
            .addAsResource(compRes.compilationPath.resolve("B.class").toFile(), "B.class")
            .addAsResource(compRes.compilationPath.resolve("B$T$1.class").toFile(), "B$T$1.class")
            .addAsResource(compRes.compilationPath.resolve("B$T$1$TT$1.class").toFile(), "B$T$1$TT$1.class")
            .addAsResource(compRes.compilationPath.resolve("B$T$2.class").toFile(), "B$T$2.class")
            .addAsResource(compRes.compilationPath.resolve("C.class").toFile(), "C.class");

        try {
            JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(new API(Arrays.asList(new ShrinkwrapArchive(api)),
                Arrays.asList(new ShrinkwrapArchive(sup))), Executors.newSingleThreadExecutor(), null,
                false, Collections.<File>emptySet(), false);

            JavaElementForest forest = analyzer.analyze();

            Assert.assertEquals(3, forest.getRoots().size());

            Iterator<TypeElement> roots = forest.getRoots().iterator();

            TypeElement A = roots.next();
            TypeElement B_T$1 = roots.next();
            TypeElement B_T$2 = roots.next();

            Assert.assertEquals("A", A.getCanonicalName());
            Assert.assertEquals("A", A.getModelElement().getQualifiedName().toString());
            Assert.assertEquals("B.T$1", B_T$1.getCanonicalName());
            Assert.assertEquals("B.T$1", B_T$1.getModelElement().getQualifiedName().toString());
            Assert.assertEquals("B.T$2", B_T$2.getCanonicalName());
            Assert.assertEquals("B.T$2", B_T$2.getModelElement().getQualifiedName().toString());
        } finally {
            deleteDir(compRes.compilationPath);
        }
    }
}
