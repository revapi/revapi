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
enum LogicalOperator {
    AND, OR;

    static LogicalOperator fromString(String str) {
        if (str == null) {
            return null;
        }

        switch (str) {
            case "and":
            case "that":
                return AND;
            case "or":
                return OR;
            default:
                return null;
        }
    }
}
