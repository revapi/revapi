/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.revapi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Quick and dirty execution time statistics collection class.
 *
 * @author Lukas Krejci
 * @since 0.4.1
 */
public final class Stats {
    private static final Map<String, Collector> COLLECTORS = new TreeMap<>();

    private Stats() {

    }

    public static Collector of(String stat) {
        if (!Revapi.TIMING_LOG.isDebugEnabled()) {
            return DummyCollector.INSTANCE;
        }

        Collector ret = COLLECTORS.get(stat);
        if (ret == null) {
            ret = new Collector();
            COLLECTORS.put(stat, ret);
        }

        return ret;
    }

    public static String asString() {
        Map<String, Collector> map = COLLECTORS.entrySet().stream()
                .sorted((e1, e2) -> (int) (e2.getValue().totalTime - e1.getValue().totalTime))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (m1, m2) -> m1, LinkedHashMap::new));

        StringBuilder bld = new StringBuilder("Stats:");

        if (map.isEmpty()) {
            return bld.append(" <none>").toString();
        }

        for (Map.Entry<String, Collector> e : map.entrySet()) {
            bld.append('\n').append(e.getKey()).append(": ").append(e.getValue());
        }

        return bld.toString();
    }

    public static class Collector {
        volatile long occurrences;
        volatile long totalTime;

        volatile long currentStartTime;

        volatile long worstTime;

        volatile Object offender;

        private Collector() {

        }

        public long start() {
            return currentStartTime = System.currentTimeMillis();
        }

        public void end(Object cause) {
            end(0, cause);
        }

        public void end(long additionalDuration, Object cause) {
            long duration = System.currentTimeMillis() - currentStartTime + additionalDuration;
            totalTime += duration;
            occurrences++;
            if (duration > worstTime) {
                worstTime = duration;
                offender = cause;
            }
        }

        public long reset() {
            return System.currentTimeMillis() - currentStartTime;
        }

        @Override
        public String toString() {
            return "{occurrences = " + occurrences + ", total = " + totalTime + "ms, average = " +
                    String.format("%.2fms", ((double) totalTime) / occurrences) + ", worstTime = " + worstTime +
                    "ms caused by " + offender + "}";
        }
    }

    private static final class DummyCollector extends Collector {
        static final DummyCollector INSTANCE = new DummyCollector();

        @Override
        public void end(long additionalDuration, Object cause) {
        }

        @Override
        public void end(Object cause) {
        }

        @Override
        public long reset() {
            return 0;
        }

        @Override public long start() {
            return 0;
        }
    }
}
