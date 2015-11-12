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

package org.revapi.java.test;

import java.util.EnumSet;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.TypeEnvironment;
import org.revapi.java.spi.UseSite;

/**
 * @author Lukas Krejci
 * @since 0.2
 */
public class CheckBaseTest {

    @Rule
    public Jar jar = new Jar();

    @Test
    public void testPubliclyUsedAs() throws Exception {
        Jar.Environment env = jar.compile("InnerClasses.java");

        TypeElement InnerClasses = env.getElementUtils().getTypeElement("InnerClasses");
        TypeElement InnerClassesA = env.getElementUtils().getTypeElement("InnerClasses.A");
        TypeElement InnerClassesB = env.getElementUtils().getTypeElement("InnerClasses.B");
        ExecutableElement method = ElementFilter.methodsIn(InnerClasses.getEnclosedElements()).get(0);
        ExecutableElement methodA = ElementFilter.methodsIn(InnerClassesA.getEnclosedElements()).get(0);

        TypeEnvironment typeEnv = TestTypeEnvironment.builder(env)
            .addUseSite(InnerClassesA, UseSite.Type.CONTAINS, InnerClassesB)
            .addUseSite(methodA, UseSite.Type.RETURN_TYPE, InnerClassesB)
            .addUseSite(method, UseSite.Type.RETURN_TYPE, InnerClassesA)
            .build();

        boolean isUsed = new DummyCheck().isPubliclyUsedAs(InnerClassesB, typeEnv,
            UseSite.Type.allBut(UseSite.Type.IS_INHERITED, UseSite.Type.CONTAINS));

        Assert.assertFalse(isUsed);

        isUsed = new DummyCheck().isPubliclyUsedAs(InnerClassesA, typeEnv,
            UseSite.Type.allBut(UseSite.Type.IS_INHERITED, UseSite.Type.CONTAINS));

        Assert.assertTrue(isUsed);
    }

    @Test
    public void testPubliclyUsedAsWithSelfReferencingClass() throws Exception {
        Jar.Environment env = jar.compile("SelfReference.java");

        TypeElement SelfReference = env.getElementUtils().getTypeElement("SelfReference");
        ExecutableElement selfReturningMethod = ElementFilter.methodsIn(SelfReference.getEnclosedElements()).get(0);

        TypeEnvironment typeEnv = TestTypeEnvironment.builder(env)
            .addUseSite(selfReturningMethod, UseSite.Type.RETURN_TYPE, SelfReference)
            .build();

        boolean isUsed = new DummyCheck()
            .isPubliclyUsedAs(SelfReference, typeEnv, UseSite.Type.all());

        Assert.assertTrue(isUsed);
    }

    private static class DummyCheck extends CheckBase {

        @Override public EnumSet<Type> getInterest() {
            return EnumSet.noneOf(Type.class);
        }
    }
}
