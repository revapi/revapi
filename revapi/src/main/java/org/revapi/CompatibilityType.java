/*
 * Copyright 2014 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;

/**
 * This class defines the types of compatibilities recognized by various extensions in the system.
 * This class mimics the Java's enum and follows the "typesafe enum" pattern but allows for extending.
 * <p/>
 * This means that one can define more types of compatibilities in the extensions and use them consistently in the rest
 * of the API checking pipeline.
 * <p/>
 * This base class doesn't actually define <i>ANY</i> compatibility types so that extensions are fully in charge of
 * what they are capable of detecting. It is advised that the names of the compatibility types are made unique per
 * extension by for example prepending the logical name of the extension to the name of the compatibility type.
 * <p/>
 * For example a java extension defines the following compatibility types:
 * <ul>
 *     <li>java.source</li>
 *     <li>java.binary</li>
 *     <li>java.semantic</li>
 *     <li>java.reflection</li>
 * </ul>
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public class CompatibilityType implements Comparable<CompatibilityType>, Iterable<CompatibilityType> {

    private static AtomicReference<CompatibilityType[]> VALUES = new AtomicReference<>(new CompatibilityType[0]);
    private static volatile int CNT;

    private final String name;
    private final int ord;

    /**
     * Creates a new compatibility type.
     *
     * @param name the unique name of the compatibility type
     *
     * @throws java.lang.IllegalArgumentException if a compatibility type of given name already exists
     */
    protected CompatibilityType(@Nonnull String name) {
        synchronized (CompatibilityType.class) {
            CompatibilityType[] values = VALUES.get();

            for (CompatibilityType t : values) {
                if (name.equals(t.name)) {
                    throw new IllegalArgumentException("A CompatibilityType instance with name '" + name + "' already" +
                        " exists.");
                }
            }

            this.name = name;
            ord = CNT++;

            CompatibilityType[] newVals = new CompatibilityType[values.length + 1];

            System.arraycopy(values, 0, newVals, 0, values.length);
            newVals[values.length] = this;

            VALUES.set(newVals);
        }
    }

    /**
     * Returns all the instances of CompatibilityType or its subclasses so far.
     */
    public static CompatibilityType[] values() {
        return VALUES.get();
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CompatibilityType)) {
            return false;
        }

        CompatibilityType base = (CompatibilityType) o;

        return ord == base.ord;
    }

    @Override
    public final int hashCode() {
        return ord;
    }

    @Override
    public String toString() {
        return name;
    }

    public final String name() {
        return name;
    }

    public final int ordinal() {
        return ord;
    }

    @Override
    public final int compareTo(@Nonnull CompatibilityType o) {
        return ord - o.ordinal();
    }

    @Override
    public final Iterator<CompatibilityType> iterator() {
        return new Iterator<CompatibilityType>() {
            //capture the current state
            private final CompatibilityType[] values = VALUES.get();
            private int idx;

            @Override
            public boolean hasNext() {
                return idx < values.length;
            }

            @Override
            public CompatibilityType next() {
                if (CNT != values.length) {
                    throw new ConcurrentModificationException();
                }

                return values[idx++];
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
