/*
 * Copyright $year Lukas Krejci
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
 *
 */
package org.revapi.java.test;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.revapi.java.spi.Util;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import java.util.List;

/**
 * @author Lukas Krejci
 * @since 0.5.1
 */
public class ToStringsTest {

    @Rule
    public Jar jar = new Jar();

    @Test
    public void testMethodParameterStringRepresentation() throws Exception {
        Jar.Environment env = jar.compile("ToStrings.java");

        Element cls = env.getElementUtils().getTypeElement("ToStrings");
        List<ExecutableElement> methods = ElementFilter.methodsIn(cls.getEnclosedElements());
        ExecutableElement method = methods.stream()
                .filter(m -> m.getSimpleName().contentEquals("methodWithTypeParamsInMethodParams"))
                .findAny().get();
        VariableElement secondParam = method.getParameters().get(1);

        String humanReadable = Util.toHumanReadableString(secondParam);
        Assert.assertEquals("void ToStrings::methodWithTypeParamsInMethodParams(int, ===java.util.function.Function<java.lang.String, ?>===, java.util.HashMap<?, ?>)",
                humanReadable);
    }
}
