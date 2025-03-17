/*
 * Copyright 2014-2023 Lukas Krejci
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
package org.revapi.java.benchmarks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.revapi.java.transforms.annotations.DownplayHarmlessAnnotationChanges;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@State(Scope.Benchmark)
public class DifferenceTransformCodesBenchmark {
    private String code;

    // Simple case where the Pattern and Predicate are effectively no-ops.
    // Pattern.compile(".*") and ignored -> true, respectively.
    private Pattern simplePattern;
    private Predicate<String> simplePredicate;

    // Complex case using DownplayHarmlessAnnotationChances which has multiple Patterns and complex Predicates.
    private DownplayHarmlessAnnotationChanges complex;

    @Setup
    public void prepareDifferenceTransformCodes() {
        this.code = "java.annotation.attributeValueChanged";

        this.simplePattern = Pattern.compile(".*");
        this.simplePredicate = __ -> true;

        this.complex = new DownplayHarmlessAnnotationChanges();
    }

    @Benchmark
    public void predicateSimple(Blackhole blackhole) {
        blackhole.consume(simplePredicate.test(code));
    }

    @Benchmark
    public void predicateComplex(Blackhole blackhole) {
        for (Predicate<String> predicate : complex.getDifferenceCodePredicates()) {
            boolean matches = predicate.test(code);
            blackhole.consume(matches);
            if (matches) {
                break;
            }
        }
    }

    @Benchmark
    public void patternNoop(Blackhole blackhole) {
        blackhole.consume(simplePattern.matcher(code).matches());
    }

    @Benchmark
    public void patternComplex(Blackhole blackhole) {
        for (Pattern pattern : complex.getDifferenceCodePatterns()) {
            boolean matches = pattern.matcher(code).matches();
            blackhole.consume(matches);
            if (matches) {
                break;
            }
        }
    }

    @Test
    public void testCodeMatching() {
        prepareDifferenceTransformCodes();

        // All cases match.
        Assertions.assertTrue(simplePredicate.test(code));
        Assertions.assertTrue(simplePattern.matcher(code).matches());

        Assertions.assertTrue(Arrays.stream(complex.getDifferenceCodePatterns())
            .anyMatch(pattern -> pattern.matcher(code).matches()));
        Assertions.assertTrue(complex.getDifferenceCodePredicates().stream()
            .anyMatch(predicate -> predicate.test(code)));
    }
}
