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

import java.util.ArrayList;
import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Report;
import org.revapi.Reporter;
import org.revapi.Revapi;
import org.revapi.java.spi.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class SupplementaryJarsTest extends AbstractJavaElementAnalyzerTest {

    @Test
    public void testSupplementaryJarsAreTakenIntoAccountWhenComputingAPI() throws Exception {
        //compile all the classes we need in 1 go
        ArchiveAndCompilationPath compRes1 = createCompiledJar("tmp1", "v1/supplementary/a/A.java",
            "v1/supplementary/b/B.java", "v1/supplementary/b/C.java");

        //now, create 2 jars out of them. Class A will be our "api" jar and the rest of the classes will form the
        //supplementary jar that needs to be present as a runtime dep of the API but isn't itself considered an API of
        //of its own.
        //We then check that types from such supplementary jar that the API jar "leaks" by exposing them as types
        //in public/protected fields/methods/method params are then considered the part of the API during api checks.

        JavaArchive apiV1 = ShrinkWrap.create(JavaArchive.class, "apiV1.jar")
            .addAsResource(compRes1.compilationPath.resolve("A.class").toFile(), "A.class");
        JavaArchive supV1 = ShrinkWrap.create(JavaArchive.class, "supV1.jar")
            .addAsResource(compRes1.compilationPath.resolve("B.class").toFile(), "B.class")
            .addAsResource(compRes1.compilationPath.resolve("B$T$1.class").toFile(), "B$T$1.class")
            .addAsResource(compRes1.compilationPath.resolve("B$T$1$TT$1.class").toFile(), "B$T$1$TT$1.class")
            .addAsResource(compRes1.compilationPath.resolve("B$T$2.class").toFile(), "B$T$2.class")
            .addAsResource(compRes1.compilationPath.resolve("C.class").toFile(), "C.class");

        //now do the same for v2
        ArchiveAndCompilationPath compRes2 = createCompiledJar("tmp2", "v2/supplementary/a/A.java",
            "v2/supplementary/b/B.java", "v2/supplementary/b/C.java");

        JavaArchive apiV2 = ShrinkWrap.create(JavaArchive.class, "apiV2.jar")
            .addAsResource(compRes2.compilationPath.resolve("A.class").toFile(), "A.class");
        JavaArchive supV2 = ShrinkWrap.create(JavaArchive.class, "supV2.jar")
            .addAsResource(compRes2.compilationPath.resolve("B.class").toFile(), "B.class")
            .addAsResource(compRes2.compilationPath.resolve("B$T$1.class").toFile(), "B$T$1.class")
            .addAsResource(compRes2.compilationPath.resolve("B$T$1$TT$1.class").toFile(), "B$T$1$TT$1.class")
            .addAsResource(compRes2.compilationPath.resolve("B$T$2.class").toFile(), "B$T$2.class")
            .addAsResource(compRes2.compilationPath.resolve("B$T$1$Private.class").toFile(), "B$T$1$Private.class")
            .addAsResource(compRes2.compilationPath.resolve("B$T$3.class").toFile(), "B$T$3.class")
            .addAsResource(compRes2.compilationPath.resolve("B$PrivateSuperClass.class").toFile(), "B$PrivateSuperClass.class")
            .addAsResource(compRes2.compilationPath.resolve("B$PrivateUsedClass.class").toFile(), "B$PrivateUsedClass.class")
            .addAsResource(compRes2.compilationPath.resolve("C.class").toFile(), "C.class");

        List<Report> allReports = new ArrayList<>();
        Reporter reporter = new CollectingReporter(allReports);

        Revapi revapi = createRevapi(reporter);

        revapi.analyze(
            AnalysisContext.builder()
                .withOldAPI(API.of(new ShrinkwrapArchive(apiV1)).supportedBy(new ShrinkwrapArchive(supV1)).build())
                .withNewAPI(API.of(new ShrinkwrapArchive(apiV2)).supportedBy(new ShrinkwrapArchive(supV2)).build())
                .withConfigurationFromJSON("{\"revapi\": {\"java\": {\"deepUseChainAnalysis\": true}}}").build()
        );

        Assert.assertEquals(6, allReports.size());
        Assert.assertTrue(containsDifference(allReports, null, "class B.T$1.Private",
                Code.CLASS_NON_PUBLIC_PART_OF_API.code()));
        Assert.assertTrue(containsDifference(allReports, null, "field B.T$2.f2", Code.FIELD_ADDED.code()));
        Assert.assertTrue(containsDifference(allReports, null, "field A.f3", Code.FIELD_ADDED.code()));
        Assert.assertTrue(containsDifference(allReports, "class B.T$2", "class B.T$2", Code.CLASS_NOW_FINAL.code()));
        Assert.assertTrue(containsDifference(allReports, null, "class B.T$3", Code.CLASS_ADDED.code()));
        Assert.assertTrue(containsDifference(allReports, null, "class B.PrivateUsedClass",
                Code.CLASS_NON_PUBLIC_PART_OF_API.code()));

        deleteDir(compRes1.compilationPath);
        deleteDir(compRes2.compilationPath);
    }
}
