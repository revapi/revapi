/*
 * Copyright 2014-2019 Lukas Krejci
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

import static java.util.stream.Collectors.toSet;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.7.0
 */
public class PackageFilterTest extends AbstractJavaElementAnalyzerTest {
    @Test
    public void testExclude() throws Exception {
        testWith("{\"revapi\": {\"filter\": {\"elements\": {\"exclude\": [{\"matcher\": \"matcher.java\", \"match\": \"type packagefilter.a.a.a.*;\"}]}}}}",
                Stream.of(
                            "class packagefilter.b.a.A",
                            "method void packagefilter.b.a.A::<init>()",
                            "class packagefilter.b.b.B",
                            "method void packagefilter.b.b.B::<init>()",
                            "class packagefilter.a.b.B",
                            "method void packagefilter.a.b.B::<init>()"
                    ).collect(toSet()));

        testWith("{\"revapi\": {\"filter\": {\"elements\": {\"include\": [{\"matcher\": \"matcher.java\", \"match\": \"type packagefilter.a.a.a.*;\"}]}}}}",
                Stream.of(
                            "class packagefilter.a.a.a.A",
                            "method void packagefilter.a.a.a.A::<init>()"
                    ).collect(toSet()));
    }

    @Test
    public void testInclude() throws Exception {
        testWith("{\"revapi\": {\"filter\": {\"elements\": {\"include\": [{\"matcher\": \"matcher.java\", \"match\": \"type packagefilter./a|b/.**.a.*;\"}]}}}}",
                Stream.of(
                            "class packagefilter.a.a.a.A",
                            "method void packagefilter.a.a.a.A::<init>()",
                            "class packagefilter.b.a.A",
                            "method void packagefilter.b.a.A::<init>()"
                    ).collect(toSet()));

    }

    private void testWith(String configJSON, Set<String> expectedResults) throws Exception {
        AbstractJavaElementAnalyzerTest.ArchiveAndCompilationPath archive = createCompiledJar("test.jar",
                "packagefilter/a/a/a/A.java", "packagefilter/a/b/B.java", "packagefilter/b/a/A.java",
                "packagefilter/b/b/B.java");

        ClassFilterTest.testWith(archive, configJSON, expectedResults);
    }
}
