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
package org.revapi;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * A helper class for {@link CorrespondenceComparatorDeducer#editDistance(BiPredicate)}.
 * <p>Strongly inspired by
 * <a href="https://en.wikipedia.org/wiki/Wagner%E2%80%93Fischer_algorithm">the wikipedia entry on the subject</a>.
 */
final class EditDistance {

    static <E> List<Pair<E>> compute(List<E> left, List<E> right, BiPredicate<? super E, ? super E> equality) {
        if (left.isEmpty()) {
            return right.stream().map(e -> new Pair<>(null, e)).collect(toList());
        }

        if (right.isEmpty()) {
            return left.stream().map(e -> new Pair<>(e, null)).collect(toList());
        }

        return resolveOperations(computeDistanceMatrix(left, right, equality), left, right);
    }

    private static <E> List<Pair<E>> resolveOperations(int[][] distanceMatrix, List<E> left, List<E> right) {
        int i = left.size();
        int j = right.size();

        List<Pair<E>> operations = new ArrayList<>();
        while (i > 0 || j > 0) {
            int deletion = i > 0 ? distanceMatrix[i - 1][j] : Integer.MAX_VALUE;
            int insertion = j > 0 ? distanceMatrix[i][j - 1] : Integer.MAX_VALUE;
            int substitution = (i > 0 && j > 0) ? distanceMatrix[i - 1][j - 1] : Integer.MAX_VALUE;

            if (deletion < insertion && deletion < substitution) {
                operations.add(new Pair<>(left.get(i - 1), null));
                i--;
            } else if (insertion < substitution) {
                operations.add(new Pair<>(null, right.get(j - 1)));
                j--;
            } else {
                operations.add(new Pair<>(left.get(i - 1), right.get(j - 1)));
                i--;
                j--;
            }
        }

        Collections.reverse(operations);
        return operations;
    }

    private static <E> int[][] computeDistanceMatrix(List<E> left, List<E> right,
            BiPredicate<? super E, ? super E> equality) {
        int llen = left.size();
        int rlen = right.size();

        int[][] distances = new int[llen + 1][rlen + 1];
        for (int j = 0; j <= rlen; ++j) {
            distances[0][j] = j;
        }

        for (int i = 1; i <= llen; ++i) {
            distances[i][0] = i;
            for (int j = 1; j <= rlen; ++j) {
                E le = left.get(i - 1);
                E re = right.get(j - 1);
                int substCost = equality.test(le, re) ? 0 : 1;

                int deletion = distances[i - 1][j] + 1;
                int insertion = distances[i][j - 1] + 1;
                int substitution = distances[i - 1][j - 1] + substCost;

                distances[i][j] = Math.min(deletion, Math.min(insertion, substitution));
            }
        }

        return distances;
    }

    static final class Pair<E> {
        final E left;
        final E right;

        Pair(E left, E right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public String toString() {
            return left + " -> " + right;
        }
    }
}
