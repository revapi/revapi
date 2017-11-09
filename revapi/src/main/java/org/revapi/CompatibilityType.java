/*
 * Copyright 2014-2017 Lukas Krejci
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

/**
 * Enumerates possible compatibility types.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public enum CompatibilityType {
    /**
     * The compatibility at source code level.
     */
    SOURCE,

    /**
     * The compatibility at the binary level.
     */
    BINARY,

    /**
     * Semantic or behavioral compatibility.
     */
    SEMANTIC,

    /**
     * Other type of compatibility not listed here.
     */
    OTHER
}
