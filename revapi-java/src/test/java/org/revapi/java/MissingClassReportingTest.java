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
package org.revapi.java;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.AnalysisResult;
import org.revapi.Difference;
import org.revapi.Report;
import org.revapi.Revapi;
import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public class MissingClassReportingTest extends AbstractJavaElementAnalyzerTest {

    private JavaArchive apiV1;
    private JavaArchive apiV2;

    private Revapi revapi;

    private List<Path> compilationPaths;

    private void compileJars() throws Exception {
        // compile all the classes we need in 1 go
        ArchiveAndCompilationPath compRes1 = createCompiledJar("tmp1", "v1/supplementary/a/A.java",
                "v1/supplementary/b/B.java", "v1/supplementary/a/C.java");

        // now, create 2 jars out of them. Class A will be our "api" jar and the rest of the classes will form the
        // supplementary jar that needs to be present as a runtime dep of the API but isn't itself considered an API of
        // of its own.
        // We then check that types from such supplementary jar that the API jar "leaks" by exposing them as types
        // in public/protected fields/methods/method params are then considered the part of the API during api checks.

        apiV1 = ShrinkWrap.create(JavaArchive.class, "apiV1.jar")
                .addAsResource(compRes1.compilationPath.resolve("A.class").toFile(), "A.class");

        // now do the same for v2
        AbstractJavaElementAnalyzerTest.ArchiveAndCompilationPath compRes2 = createCompiledJar("tmp2",
                "v2/supplementary/a/A.java", "v2/supplementary/b/B.java", "v2/supplementary/a/C.java");

        apiV2 = ShrinkWrap.create(JavaArchive.class, "apiV2.jar")
                .addAsResource(compRes2.compilationPath.resolve("A.class").toFile(), "A.class");

        compilationPaths = Arrays.asList(compRes1.compilationPath, compRes2.compilationPath);
    }

    @Before
    public void setup() throws Exception {
        compileJars();
        revapi = createRevapi(CollectingReporter.class);
    }

    @After
    public void cleanup() throws Exception {
        compilationPaths.forEach(p -> {
            try {
                deleteDir(p);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to delete the compilation path:" + p);
            }
        });
    }

    @Test
    public void testErrorsOutOnMissingClasses() throws Exception {
        try {
            revapi.analyze(AnalysisContext.builder().withOldAPI(API.of(new ShrinkwrapArchive(apiV1)).build())
                    .withNewAPI(API.of(new ShrinkwrapArchive(apiV2)).build())
                    .withConfigurationFromJSON("{\"revapi\" : { \"java\" : { \"missing-classes\" : \"error\" }}}")
                    .build());

            Assert.fail();
        } catch (RuntimeException e) {
            // expected
        }
    }

    @Test
    public void testReportsMissingClasses() throws Exception {
        AnalysisContext ctx = AnalysisContext.builder(revapi).withOldAPI(API.of(new ShrinkwrapArchive(apiV1)).build())
                .withNewAPI(API.of(new ShrinkwrapArchive(apiV2)).build()).withConfigurationFromJSON(
                        "{\"revapi\" : { \"java\" : { \"missing-classes\" : {\"behavior\" : \"report\" }}}}")
                .build();

        revapi.validateConfiguration(ctx);
        List<Report> allReports = revapi.analyze(ctx).getExtensions().getFirstExtension(CollectingReporter.class, null)
                .getReports();

        Assert.assertEquals(3, allReports.size());
        Assert.assertTrue(containsDifference(allReports, "missing-class B.T$2", "missing-class B.T$2",
                Code.MISSING_IN_NEW_API.code()));
        Assert.assertTrue(containsDifference(allReports, "missing-class B.T$2", "missing-class B.T$2",
                Code.MISSING_IN_OLD_API.code()));
        Assert.assertTrue(containsDifference(allReports, null, "missing-class B.T$3", Code.MISSING_IN_NEW_API.code()));
        Assert.assertTrue(containsDifference(allReports, null, "field A.f3", Code.FIELD_ADDED.code()));

        boolean containsMissingOld = false;
        boolean containsMissingNew = false;

        for (Difference d : allReports.get(0).getDifferences()) {
            if (d.code.equals(Code.MISSING_IN_NEW_API.code())) {
                containsMissingNew = true;
            }

            if (d.code.equals(Code.MISSING_IN_OLD_API.code())) {
                containsMissingOld = true;
            }
        }

        Assert.assertTrue(containsMissingOld);
        Assert.assertTrue(containsMissingNew);
    }

    @Test
    public void testIgnoresMissingClasses() throws Exception {
        AnalysisResult res = revapi
                .analyze(AnalysisContext.builder(revapi).withOldAPI(API.of(new ShrinkwrapArchive(apiV1)).build())
                        .withNewAPI(API.of(new ShrinkwrapArchive(apiV2)).build())
                        .withConfigurationFromJSON(
                                "{\"revapi\" : { \"java\" : { \"missing-classes\" : {\"behavior\" : \"ignore\" }}}}")
                        .build());

        res.throwIfFailed();

        List<Report> allReports = res.getExtensions().getFirstExtension(CollectingReporter.class, null).getReports();

        Assert.assertEquals(1, allReports.size());
        Assert.assertTrue(containsDifference(allReports, null, "field A.f3", Code.FIELD_ADDED.code()));
    }

    @Test
    public void testReportMissingSuperType() throws Exception {
        ArchiveAndCompilationPath v1 = null, v2 = null;

        try {
            v1 = createCompiledJar("superType1", "v1/supplementary/superType/A.java",
                    "v1/supplementary/superType/B.java");
            v2 = createCompiledJar("superType2", "v2/supplementary/superType/A.java",
                    "v2/supplementary/superType/B.java");

            JavaArchive a1 = ShrinkWrap.create(JavaArchive.class, "superTypeV1.jar")
                    .addAsResource(v1.compilationPath.resolve("A.class").toFile(), "A.class")
                    .addAsResource(v1.compilationPath.resolve("B.class").toFile(), "B.class");
            JavaArchive a2 = ShrinkWrap.create(JavaArchive.class, "superTypeV2.jar")
                    .addAsResource(v2.compilationPath.resolve("B.class").toFile(), "B.class");

            API oldApi = API.of(new ShrinkwrapArchive(a1)).build();
            API newApi = API.of(new ShrinkwrapArchive(a2)).build();

            AnalysisResult res = revapi
                    .analyze(AnalysisContext.builder().withOldAPI(oldApi).withNewAPI(newApi).build());

            List<Report> allReports = res.getExtensions().getFirstExtension(CollectingReporter.class, null)
                    .getReports();

            Assert.assertEquals(2, allReports.size());
            Assert.assertTrue(containsDifference(allReports, "class B", "class B", Code.MISSING_NEW_SUPERTYPE.code()));
            Assert.assertTrue(containsDifference(allReports, "class A", null, Code.CLASS_REMOVED.code()));

            res = revapi.analyze(AnalysisContext.builder().withOldAPI(newApi).withNewAPI(oldApi).build());

            allReports = res.getExtensions().getFirstExtension(CollectingReporter.class, null).getReports();

            Assert.assertEquals(2, allReports.size());
            Assert.assertTrue(containsDifference(allReports, "class B", "class B", Code.MISSING_OLD_SUPERTYPE.code()));
            Assert.assertTrue(containsDifference(allReports, null, "class A", Code.CLASS_ADDED.code()));
        } finally {
            compilationPaths = new ArrayList<>(compilationPaths);
            if (v1 != null) {
                compilationPaths.add(v1.compilationPath);
            }
            if (v2 != null) {
                compilationPaths.add(v2.compilationPath);
            }
        }
    }

    @Test
    public void testMissingClassFiltering() throws Exception {
        AnalysisContext ctx = AnalysisContext.builder(revapi).withOldAPI(API.of(new ShrinkwrapArchive(apiV1)).build())
                .withNewAPI(API.of(new ShrinkwrapArchive(apiV2)).build())
                .withConfigurationFromJSON(
                        "[{\"extension\": \"revapi.java\", \"configuration\": {\"missing-classes\": {\"behavior\": \"report\"}}},"
                                + "{\"extension\": \"revapi.filter\", \"configuration\": {\"archives\": {\"include\": [\"apiV..jar\"]}}}]")
                .build();

        revapi.validateConfiguration(ctx);
        List<Report> allReports = revapi.analyze(ctx).getExtensions().getFirstExtension(CollectingReporter.class, null)
                .getReports();

        Assert.assertEquals(1, allReports.size());
        Assert.assertTrue(containsDifference(allReports, null, "field A.f3", Code.FIELD_ADDED.code()));
    }

    @Test
    public void testMissingClassHasValidPackage() throws Exception {
        AnalysisContext ctx = AnalysisContext.builder(revapi).withOldAPI(API.of(new ShrinkwrapArchive(apiV1)).build())
                .withNewAPI(API.of(new ShrinkwrapArchive(apiV2)).build()).withConfigurationFromJSON(
                        "{\"revapi\" : { \"java\" : { \"missing-classes\" : {\"behavior\" : \"report\" }}}}")
                .build();

        revapi.validateConfiguration(ctx);
        List<Report> allReports = revapi.analyze(ctx).getExtensions().getFirstExtension(CollectingReporter.class, null)
                .getReports();

        for (Report r : allReports) {
            if (r.getNewElement() != null && r.getNewElement() instanceof TypeElement) {
                Assert.assertNotNull(
                        ((JavaModelElement) r.getNewElement()).getDeclaringElement().getEnclosingElement());
            }
            if (r.getOldElement() != null && r.getNewElement() instanceof TypeElement) {
                Assert.assertNotNull(
                        ((JavaModelElement) r.getOldElement()).getDeclaringElement().getEnclosingElement());
            }
        }
    }
}
