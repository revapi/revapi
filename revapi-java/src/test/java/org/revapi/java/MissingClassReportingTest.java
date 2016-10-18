package org.revapi.java;

import java.util.ArrayList;
import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.Report;
import org.revapi.Reporter;
import org.revapi.Revapi;
import org.revapi.java.spi.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class MissingClassReportingTest extends AbstractJavaElementAnalyzerTest {

    private JavaArchive apiV1;
    private JavaArchive apiV2;

    private List<Report> allReports = new ArrayList<>();
    private Revapi revapi;

    private void compileJars() throws Exception {
        //compile all the classes we need in 1 go
        AbstractJavaElementAnalyzerTest.ArchiveAndCompilationPath compRes1 = createCompiledJar("tmp1",
            "v1/supplementary/a/A.java",
            "v1/supplementary/b/B.java", "v1/supplementary/a/C.java");

        //now, create 2 jars out of them. Class A will be our "api" jar and the rest of the classes will form the
        //supplementary jar that needs to be present as a runtime dep of the API but isn't itself considered an API of
        //of its own.
        //We then check that types from such supplementary jar that the API jar "leaks" by exposing them as types
        //in public/protected fields/methods/method params are then considered the part of the API during api checks.

        apiV1 = ShrinkWrap.create(JavaArchive.class, "apiV1.jar")
            .addAsResource(compRes1.compilationPath.resolve("A.class").toFile(), "A.class");
//        supV1 = ShrinkWrap.create(JavaArchive.class, "supV1.jar")
//            .addAsResource(compRes1.compilationPath.resolve("B.class").toFile(), "B.class")
//            .addAsResource(compRes1.compilationPath.resolve("B$T$1.class").toFile(), "B$T$1.class")
//            .addAsResource(compRes1.compilationPath.resolve("B$T$1$TT$1.class").toFile(), "B$T$1$TT$1.class")
//            .addAsResource(compRes1.compilationPath.resolve("B$T$2.class").toFile(), "B$T$2.class")
//            .addAsResource(compRes1.compilationPath.resolve("C.class").toFile(), "C.class");

        //now do the same for v2
        AbstractJavaElementAnalyzerTest.ArchiveAndCompilationPath compRes2 = createCompiledJar("tmp2",
            "v2/supplementary/a/A.java",
            "v2/supplementary/b/B.java", "v2/supplementary/a/C.java");

        apiV2 = ShrinkWrap.create(JavaArchive.class, "apiV2.jar")
            .addAsResource(compRes2.compilationPath.resolve("A.class").toFile(), "A.class");
//        supV2 = ShrinkWrap.create(JavaArchive.class, "supV2.jar")
//            .addAsResource(compRes2.compilationPath.resolve("B.class").toFile(), "B.class")
//            .addAsResource(compRes2.compilationPath.resolve("B$T$1.class").toFile(), "B$T$1.class")
//            .addAsResource(compRes2.compilationPath.resolve("B$T$1$TT$1.class").toFile(), "B$T$1$TT$1.class")
//            .addAsResource(compRes2.compilationPath.resolve("B$T$2.class").toFile(), "B$T$2.class")
//            .addAsResource(compRes2.compilationPath.resolve("B$T$1$Private.class").toFile(), "B$T$1$Private.class")
//            .addAsResource(compRes2.compilationPath.resolve("C.class").toFile(), "C.class");
    }

    @Before
    public void setup() throws Exception {
        compileJars();
        allReports.clear();

        Reporter reporter = new CollectingReporter(allReports);

        revapi = createRevapi(reporter);
    }

    @After
    public void teardown() throws Exception {
        revapi.close();
    }

    @Test
    public void testErrorsOutOnMissingClasses() throws Exception {
        try {
            revapi.analyze(
                AnalysisContext.builder()
                    .withOldAPI(API.of(new ShrinkwrapArchive(apiV1)).build())
                    .withNewAPI(API.of(new ShrinkwrapArchive(apiV2)).build())
                    .withConfigurationFromJSON("{\"revapi\" : { \"java\" : { \"missing-classes\" : \"error\" }}}")
                    .build()
            );

            Assert.fail();
        } catch (RuntimeException e) {
            //expected
        }
    }

    @Test
    public void testReportsMissingClasses() throws Exception {
        AnalysisContext ctx = AnalysisContext.builder()
                .withOldAPI(API.of(new ShrinkwrapArchive(apiV1)).build())
                .withNewAPI(API.of(new ShrinkwrapArchive(apiV2)).build())
                .withConfigurationFromJSON(
                        "{\"revapi\" : { \"java\" : { \"missing-classes\" : {\"behavior\" : \"report\" }}}}").build();

        revapi.validateConfiguration(ctx);
        revapi.analyze(ctx);

        Assert.assertEquals(3, allReports.size());
        Assert.assertTrue(containsDifference(allReports, "missing-class B.T$2", "missing-class B.T$2",
            Code.MISSING_IN_NEW_API.code()));
        Assert.assertTrue(containsDifference(allReports, "missing-class B.T$2", "missing-class B.T$2",
            Code.MISSING_IN_OLD_API.code()));
        Assert.assertTrue(containsDifference(allReports, null, "missing-class B.T$3",
            Code.MISSING_IN_NEW_API.code()));
        Assert.assertTrue(containsDifference(allReports, null, "field A.f3",
            Code.FIELD_ADDED.code()));

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
        revapi.analyze(
            AnalysisContext.builder()
                .withOldAPI(API.of(new ShrinkwrapArchive(apiV1)).build())
                .withNewAPI(API.of(new ShrinkwrapArchive(apiV2)).build())
                .withConfigurationFromJSON(
                    "{\"revapi\" : { \"java\" : { \"missing-classes\" : {\"behavior\" : \"ignore\" }}}}").build()
        );

        Assert.assertEquals(1, allReports.size());
        Assert.assertTrue(containsDifference(allReports, null, "field A.f3", Code.FIELD_ADDED.code()));
    }

}
