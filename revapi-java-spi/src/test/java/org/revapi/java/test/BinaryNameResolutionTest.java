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
package org.revapi.java.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Objects;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.revapi.java.spi.Util;

@RunWith(MockitoJUnitRunner.class)
public class BinaryNameResolutionTest {

    @Mock
    private Elements elements;

    @Mock
    private TypeElement fakeResult;

    @Test
    public void testNonNestedWithDollars() {
        String name = "pkg.My$Non$Nested$Class";
        doTest(name, name);
    }

    @Test
    public void testNested() {
        String binaryName = "pkg.My$$Nested$Class";

        doTest(binaryName, "pkg.My$$Nested$Class");

        doTest(binaryName, "pkg.My$$Nested.Class");
        doTest(binaryName, "pkg.My$.Nested$Class");
        doTest(binaryName, "pkg.My.$Nested$Class");

        doTest(binaryName, "pkg.My$.Nested.Class");
        doTest(binaryName, "pkg.My.$Nested.Class");
    }


    @Test
    public void testLeadingDollar() {
        String name = "$LeadingZero.$Test.Like.Serious.$Class";
        doTest(name, name);
    }

    @Test
    public void testTrailingDollar() {
        String name = "pkg.TrailingZero$.Class$";
        doTest(name, name);
    }

    @Test
    public void testConsecutiveDollarsInName() {
        String binaryName = "Test$$Test$$$$Class";

        doTest(binaryName, "Test$$Test$$$$Class");
        doTest(binaryName, "Test$$Test$$$.Class");
        doTest(binaryName, "Test$$Test$$.$Class");
        doTest(binaryName, "Test$$Test$.$$Class");
        doTest(binaryName, "Test$$Test.$$$Class");
        doTest(binaryName, "Test$.Test$$$$Class");
        doTest(binaryName, "Test.$Test$$$$Class");

        doTest(binaryName, "Test..Test$$$$Class", false);
        doTest(binaryName, "Test$$Test..$$Class", false);
        doTest(binaryName, "Test$$Test$..$Class", false);
        doTest(binaryName, "Test$$Test$$..Class", false);
    }

    @Test
    public void testConsecutiveDollarsInNameWithNested() {
        String binaryName = "pkg.$Test$.$$Class";

        doTest(binaryName, "pkg.$Test$.$$Class");
        doTest(binaryName, "pkg.$Test$.$.Class");

        doTest(binaryName, "pkg..Test$.$$Class", false);
        doTest(binaryName, "pkg.$Test..$$Class", false);
        doTest(binaryName, "pkg.$Test$..$Class", false);
        doTest(binaryName, "pkg.$Test$$..Class", false);
    }

    @Test
    public void testAnonymousClassesRejected() {
        doTest("pkg.Test$1", null, false);

        // verify we don't try to do lookups of invalid class names
        verify(elements, times(1)).getTypeElement(any());
    }

    private void doTest(String binaryName, String fqn) {
        doTest(binaryName, fqn, true);
    }

    private void doTest(String binaryName, String fqn, boolean expected) {
        reset(elements);
        when(elements.getTypeElement(argThat(a -> Objects.equals(fqn, a.toString())))).thenReturn(fakeResult);

        TypeElement res = Util.findTypeByBinaryName(elements, binaryName);

        if (expected) {
            assertNotNull(res);
        } else {
            assertNull(res);
        }
    }
}
