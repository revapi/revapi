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

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Criticality represents the assigned importance of a {@link Difference}. While difference has its classifications and
 * severities, criticality expresses how that classification is perceived in the concrete case of the difference.
 *
 * An API author might consider it OK to have breaking changes in certain part of API, yet wants to highlight to the
 * users in reports that those are indeed breaking. It would not have therefore been enough to just reclassify the
 * difference as non-breaking.
 *
 * There are a couple of predefined criticalities that are always present in the analysis and are ready to use:
 *
 * <ol>
 * <li>{@link Criticality#ALLOWED}
 * <li>{@link Criticality#DOCUMENTED}
 * <li>{@link Criticality#HIGHLIGHT}
 * <li>{@link Criticality#ERROR}
 * </ol>
 */
public final class Criticality implements Comparable<Criticality> {
    /**
     * Differences with this criticality are allowed and considered OK. The {@link #getLevel() level} of this
     * criticality is 1000.
     */
    public static final Criticality ALLOWED = new Criticality("allowed", 1000);

    /**
     * Differences with this criticality are necessary in the project and are documented. The {@link #getLevel() level}
     * of this criticality is 2000.
     */
    public static final Criticality DOCUMENTED = new Criticality("documented", 2000);

    /**
     * Differences with this criticality are necessary in the project, are documented and are very important/severe so
     * should be highlighted in the reports. The {@link #getLevel() level} of this criticality is 3000.
     */
    public static final Criticality HIGHLIGHT = new Criticality("highlight", 3000);

    /**
     * Differences with this criticality are not allowed and should be dealt with in the checked codebase. The
     * {@link #getLevel() level} of this criticality is the maximum integer value. There can be no criticality more
     * severe than this.
     */
    public static final Criticality ERROR = new Criticality("error", Integer.MAX_VALUE);

    private final String name;
    private final int level;

    /**
     * Creates a new criticality instance.
     *
     * @param name
     *            the human readable name of the criticality
     * @param level
     *            how critical it is. A non-negative integer
     */
    public Criticality(String name, int level) {
        this.name = name;
        this.level = level;
    }

    /**
     * The default set of criticalities known to the pipeline configuration.
     */
    public static Set<Criticality> defaultCriticalities() {
        Set<Criticality> ret = new HashSet<>(4, 1.0f);
        ret.add(ALLOWED);
        ret.add(DOCUMENTED);
        ret.add(HIGHLIGHT);
        ret.add(ERROR);

        return ret;
    }

    /**
     * The default mapping from difference severity to criticality used in the analysis context if not configured
     * otherwise.
     */
    public static Map<DifferenceSeverity, Criticality> defaultSeverityMapping() {
        Map<DifferenceSeverity, Criticality> ret = new EnumMap<>(DifferenceSeverity.class);
        ret.put(DifferenceSeverity.EQUIVALENT, ALLOWED);
        ret.put(DifferenceSeverity.NON_BREAKING, DOCUMENTED);
        ret.put(DifferenceSeverity.POTENTIALLY_BREAKING, ERROR);
        ret.put(DifferenceSeverity.BREAKING, ERROR);

        return ret;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Criticality that = (Criticality) o;
        return level == that.level && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, level);
    }

    @Override
    public String toString() {
        return "Criticality[" + "name='" + name + '\'' + ", level=" + level + ']';
    }

    @Override
    public int compareTo(Criticality o) {
        return this.level - o.level;
    }
}
