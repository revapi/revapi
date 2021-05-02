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
package org.revapi.java;

import java.util.Arrays;

public class ExpectedValues {
    private static final String FULL_VERSION = System.getProperty("java.version");
    private static final int MAJOR_VERSION;

    static {
        if (FULL_VERSION.startsWith("1.8")) {
            MAJOR_VERSION = 8;
        } else {
            String major = FULL_VERSION.substring(0, FULL_VERSION.indexOf('.'));
            MAJOR_VERSION = Integer.parseInt(major);
        }
    }

    public static <T> T dependingOnJavaVersion(int ver1, T val1, int ver2, T val2) {
        if (ver1 > ver2) {
            if (MAJOR_VERSION >= ver1) {
                return val1;
            }
        }

        if (MAJOR_VERSION >= ver2) {
            return val2;
        }

        throw new IllegalArgumentException(
                "no version from " + Arrays.asList(ver1, ver2) + " applicable to " + MAJOR_VERSION);
    }

    public static <T> T dependingOnJavaVersion(int ver1, T val1, int ver2, T val2, int ver3, T val3) {
        int[] sorted = new int[] { ver1, ver2, ver3 };
        Arrays.sort(sorted);

        for (int i = 2; i >= 0; --i) {
            int ver = sorted[i];
            if (MAJOR_VERSION >= ver) {
                if (ver == ver1) {
                    return val1;
                } else if (ver == ver2) {
                    return val2;
                } else {
                    return val3;
                }
            }
        }

        throw new IllegalArgumentException(
                "no version from " + Arrays.asList(ver1, ver2, ver3) + " applicable to " + MAJOR_VERSION);
    }

    public static <T> T dependingOnJavaVersion(int ver1, T val1, int ver2, T val2, int ver3, T val3, int ver4, T val4) {
        int[] sorted = new int[] { ver1, ver2, ver3, ver4 };
        Arrays.sort(sorted);

        for (int i = 3; i >= 0; --i) {
            int ver = sorted[i];
            if (MAJOR_VERSION >= ver) {
                if (ver == ver1) {
                    return val1;
                } else if (ver == ver2) {
                    return val2;
                } else if (ver == ver3) {
                    return val3;
                } else {
                    return val4;
                }
            }
        }

        throw new IllegalArgumentException(
                "no version from " + Arrays.asList(ver1, ver2, ver3, ver4) + " applicable to " + MAJOR_VERSION);
    }
}
