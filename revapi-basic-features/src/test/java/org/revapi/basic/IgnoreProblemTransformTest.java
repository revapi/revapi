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

package org.revapi.basic;

import java.util.HashMap;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

import org.revapi.API;
import org.revapi.Configuration;
import org.revapi.Element;
import org.revapi.MatchReport;
import org.revapi.simple.SimpleElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class IgnoreProblemTransformTest {

    private static class DummyElement extends SimpleElement {

        private final String name;

        public DummyElement(String name) {
            this.name = name;
        }

        @Override
        public API getApi() {
            return null;
        }

        @Override
        public String getFullHumanReadableString() {
            return name;
        }

        @Override
        public int compareTo(Element o) {
            if (!(o instanceof DummyElement)) {
                return -1;
            }

            return name.compareTo(((DummyElement) o).name);
        }
    }

    @Test
    public void testSimpleTextMatch() throws Exception {
        DummyElement oldE = new DummyElement("a");
        DummyElement newE = new DummyElement("b");

        MatchReport.Problem problem = MatchReport.Problem.builder().withCode("c").build();

        IgnoreProblemTransform t = new IgnoreProblemTransform();

        Configuration config = new Configuration(Locale.getDefault(), new HashMap<String, String>(), null, null);
        config.getProperties().put("revapi.ignore.1.code", "c");

        t.initialize(config);
        problem = t.transform(oldE, newE, problem);
        Assert.assertNull(problem);
    }

    @Test
    public void testRegexMatch() throws Exception {
        DummyElement oldE = new DummyElement("a");
        DummyElement newE = new DummyElement("b");

        MatchReport.Problem problem = MatchReport.Problem.builder().withCode("c").build();

        IgnoreProblemTransform t = new IgnoreProblemTransform();

        Configuration config = new Configuration(Locale.getDefault(), new HashMap<String, String>(), null, null);
        config.getProperties().put("revapi.ignore.1.regex", "true");
        config.getProperties().put("revapi.ignore.1.code", "[c]*");

        t.initialize(config);
        problem = t.transform(oldE, newE, problem);
        Assert.assertNull(problem);
    }

    //TODO add tests for old and new element matching
}
