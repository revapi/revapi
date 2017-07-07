/*
 * Copyright 2015-2017 Lukas Krejci
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
 *
 */

package org.revapi.java.matcher;

/**
 * @author Lukas Krejci
 */
enum ComparisonOperator {
    EQ("=") {
        @Override
        public boolean evaluate(int a, int b) {
            return a == b;
        }
    },
    LT("<") {
        @Override
        public boolean evaluate(int a, int b) {
            return a < b;
        }
    },
    GT(">") {
        @Override
        public boolean evaluate(int a, int b) {
            return a > b;
        }
    },
    NE("!=") {
        @Override
        public boolean evaluate(int a, int b) {
            return a != b;
        }
    },
    LTE("<=") {
        @Override
        public boolean evaluate(int a, int b) {
            return a <= b;
        }
    },
    GTE(">=") {
        @Override
        public boolean evaluate(int a, int b) {
            return a >= b;
        }
    };

    private final String symbol;

    ComparisonOperator(String symbol) {
        this.symbol = symbol;
    }

    public static ComparisonOperator fromSymbol(String symbol) {
        for (ComparisonOperator o : ComparisonOperator.values()) {
            if (o.symbol.equals(symbol)) {
                return o;
            }
        }

        return null;
    }

    public String getSymbol() {
        return symbol;
    }

    public abstract boolean evaluate(int a, int b);
}
