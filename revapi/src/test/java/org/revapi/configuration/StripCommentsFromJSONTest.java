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
        Assert.assertEquals("\ncode/code\n\n code", StripCommentsFromJSON.string(json));
    }

    @Test
    public void testMultiLine() {
        String json = "/code/* comment //code * code *//code";
        Assert.assertEquals("/code/code", StripCommentsFromJSON.string(json));
    }
}
