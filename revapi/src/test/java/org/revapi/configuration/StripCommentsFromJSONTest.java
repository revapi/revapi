/*
 * Copyright 2015 Lukas Krejci
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
 */

package org.revapi.configuration;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class StripCommentsFromJSONTest {

    @Test
    public void testSingleLine() {
        String json = "//comment\ncode/code//comment\n//comment\n code";
        Assert.assertEquals("\ncode/code\n\n code", JSONUtil.stripComments(json));

        json = "//asdf";
        Assert.assertEquals("", JSONUtil.stripComments(json));

        json = "code\"\\\"//\"/**/asdf\"/*\\\"*/\"//asdf";
        Assert.assertEquals("code\"\\\"//\"asdf\"/*\\\"*/\"", JSONUtil.stripComments(json));
    }

    @Test
    public void testMultiLine() {
        String json = "/code/* comment \n//code * code\"asdf\" \n\n\n*//code";
        Assert.assertEquals("/code/code", JSONUtil.stripComments(json));
    }
}
