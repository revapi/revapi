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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.revapi.base.BaseElement;

class ReportComparatorTest {

    @Test
    void canFindBaseType() {
        Class<?> cls = ReportComparator.getBaseType(El1.class);
        assertEquals(El1.class, cls);

        cls = ReportComparator.getBaseType(El2.class);
        assertEquals(El1.class, cls);
    }

    private static class El1 extends BaseElement<El1> {
        protected El1() {
            super(null);
        }

        @Override
        public int compareTo(El1 o) {
            return 0;
        }
    }

    private static class El2 extends El1 {
    }
}
