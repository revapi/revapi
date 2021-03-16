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
package org.revapi.java.compilation;

/**
 * @author Lukas Krejci
 * 
 * @since 0.9.0
 */
public interface InclusionFilter {

    static InclusionFilter acceptAll() {
        return new InclusionFilter() {
            @Override
            public boolean accepts(String typeBinaryName, String typeCanonicalName) {
                return true;
            }

            @Override
            public boolean rejects(String typeBinaryName, String typeCanonicalName) {
                return false;
            }

            @Override
            public boolean defaultCase() {
                return true;
            }
        };
    }

    boolean accepts(String typeBinaryName, String typeCanonicalName);

    boolean rejects(String typeBinaryName, String typeCanonicalName);

    boolean defaultCase();
}
