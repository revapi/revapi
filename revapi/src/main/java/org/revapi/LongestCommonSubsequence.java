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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * A helper class for {@link CorrespondenceComparatorDeducer#diff(BiPredicate)} implementing the diffing algorithm.
 * <p>Strongly inspired by
 * <a href="https://en.wikipedia.org/wiki/Longest_common_subsequence_problem">the wikipedia entry on the subject</a>.
 * @param <E>
 */
final class LongestCommonSubsequence<E> {
    static <E> List<Pair<E>> sort(List<E> left, List<E> right, BiPredicate<? super E, ? super E> equality) {
        return backtrack(computeBacktrackMatrix(left, right, equality), left, right, equality);
    }

    private static <E> int[][] computeBacktrackMatrix(List<E> left, List<E> right,
            BiPredicate<? super E, ? super E> equality) {
        int[][] matrix = new int[left.size() + 1][right.size() + 1];
        for (int i = 1; i < matrix.length; i++) {
            for (int j = 1; j < matrix[0].length; j++) {
                if (equality.test(left.get(i - 1), right.get(j - 1))) {
                    matrix[i][j] = 1 + matrix[i - 1][j - 1];
                } else {
                    matrix[i][j] = Math.max(matrix[i - 1][j], matrix[i][j - 1]);
                }
            }
        }

        return matrix;
    }

    private static <E> List<Pair<E>> backtrack(int[][] backtrackMatrix, List<E> left, List<E> right,
            BiPredicate<? super E, ? super E> equality) {
        int i = left.size();
        int j = right.size();

        List<Pair<E>> order = new ArrayList<>();
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && equality.test(left.get(i - 1), right.get(j - 1))) {
                order.add(new Pair<>(left.get(i - 1), right.get(j - 1)));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || backtrackMatrix[i][j - 1] >= backtrackMatrix[i - 1][j])) {
                order.add(new Pair<>(null, right.get(j - 1)));
                j--;
            } else if (i > 0 && (j == 0 || backtrackMatrix[i][j - 1] < backtrackMatrix[i - 1][j])) {
                order.add(new Pair<>(left.get(i - 1), null));
                i--;
            }
        }

        Collections.reverse(order);
        return order;
    }

    static final class Pair<E> {
        final E left;
        final E right;

        Pair(E left, E right) {
            this.left = left;
            this.right = right;
        }
    }
}
