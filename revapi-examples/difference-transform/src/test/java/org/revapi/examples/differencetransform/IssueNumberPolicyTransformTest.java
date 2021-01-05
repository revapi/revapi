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
package org.revapi.examples.differencetransform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.revapi.Criticality;
import org.revapi.Difference;
import org.revapi.TransformationResult;

class IssueNumberPolicyTransformTest {

    IssueNumberPolicyTransform<?> transform = new IssueNumberPolicyTransform<>();

    @Test
    void testDifferenceWithoutIssueNumberFails() {
        TransformationResult res = transform.tryTransform(null, null, Difference.builder().build());
        assertSame(TransformationResult.Resolution.REPLACE, res.getResolution());
        assertEquals(1, res.getDifferences().size());
        assertSame(Criticality.ERROR, res.getDifferences().iterator().next().criticality);
    }

    @Test
    void testDifferenceWithIssueNumberSucceeds() {
        TransformationResult res = transform.tryTransform(null, null, Difference.builder()
                .addAttachment("issue-no", "teh-issue")
                .build());
        assertSame(TransformationResult.Resolution.KEEP, res.getResolution());
    }
}
