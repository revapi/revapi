/*
 * Copyright 2017 Lukas Krejci
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

package org.revapi.maven;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.StringWriter;

import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 0.9.0
 */
public class XmlUtilTest {

    @Test
    public void testIndentationSizeEstimation() throws Exception {
        String text = "a\n  b\n  c\n   d";

        assertEquals(2, XmlUtil.estimateIndentationSize(new BufferedReader(new StringReader(text))));
    }

    @Test
    public void testPrettyPrintingXml() throws Exception {
        String text = "<a><b><c>asdf</c><d/></b></a>";

        XmlPlexusConfiguration xml = new XmlPlexusConfiguration(Xpp3DomBuilder.build(new StringReader(text)));

        StringWriter wrt = new StringWriter();

        XmlUtil.toIndentedString(xml, 2, 0, wrt);

        String pretty = wrt.toString();

        assertEquals("<a>\n  <b>\n    <c>asdf</c>\n    <d/>\n  </b>\n</a>", pretty);
    }
}
