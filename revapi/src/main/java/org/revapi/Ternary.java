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

import java.util.function.Supplier;

/**
 * Ternary logic using the Kleene algebra. The {@link #UNDECIDED} value means just that - it doesn't mean "both true and
 * false".
 */
public enum Ternary {
    TRUE, FALSE, UNDECIDED;

    public static Ternary fromBoolean(boolean value) {
        return value ? TRUE : FALSE;
    }

    /**
     * @param undecidedValue
     *            the boolean value to convert the {@link #UNDECIDED} value to.
     * 
     * @return the value as a boolean
     */
    public boolean toBoolean(boolean undecidedValue) {
        switch (this) {
        case TRUE:
            return true;
        case FALSE:
            return false;
        case UNDECIDED:
            return undecidedValue;
        default:
            throw new IllegalStateException("Unhandled ternary value.");
        }
    }

    public Ternary negate() {
        switch (this) {
        case TRUE:
            return FALSE;
        case FALSE:
            return TRUE;
        case UNDECIDED:
            return UNDECIDED;
        default:
            throw new IllegalStateException("Unhandled ternary value.");
        }
    }

    public Ternary and(Ternary other) {
        switch (this) {
        case TRUE:
            return other;
        case FALSE:
            return this;
        case UNDECIDED:
            return other == FALSE ? other : this;
        default:
            throw new IllegalStateException("Unhandled ternary value.");
        }
    }

    /**
     * Short-circuiting version of {@link #and(Ternary)}. Will only evaluate the argument if necessary.
     */
    public Ternary and(Supplier<Ternary> other) {
        switch (this) {
        case TRUE:
            return other.get();
        case FALSE:
            return this;
        case UNDECIDED:
            Ternary otherVal = other.get();
            return otherVal == FALSE ? otherVal : this;
        default:
            throw new IllegalStateException("Unhandled ternary value.");
        }
    }

    public Ternary or(Ternary other) {
        switch (this) {
        case TRUE:
            return this;
        case FALSE:
            return other;
        case UNDECIDED:
            return other == TRUE ? other : this;
        default:
            throw new IllegalStateException("Unhandled ternary value.");
        }
    }

    /**
     * Short-circuiting version of {@link #or(Ternary)}. Will only evaluate the argument if necessary.
     */
    public Ternary or(Supplier<Ternary> other) {
        switch (this) {
        case TRUE:
            return this;
        case FALSE:
            return other.get();
        case UNDECIDED:
            Ternary otherVal = other.get();
            return otherVal == TRUE ? otherVal : this;
        default:
            throw new IllegalStateException("Unhandled ternary value.");
        }
    }

    public Ternary implies(Ternary other) {
        switch (this) {
        case TRUE:
            return other;
        case FALSE:
            return TRUE;
        case UNDECIDED:
            return other == TRUE ? other : this;
        default:
            throw new IllegalStateException("Unhandled ternary value.");
        }
    }

    public Ternary implies(Supplier<Ternary> other) {
        switch (this) {
        case TRUE:
            return other.get();
        case FALSE:
            return TRUE;
        case UNDECIDED:
            Ternary otherVal = other.get();
            return otherVal == TRUE ? otherVal : this;
        default:
            throw new IllegalStateException("Unhandled ternary value.");
        }
    }
}
