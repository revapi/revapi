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

package org.revapi.java;

import java.io.File;
import java.io.ObjectStreamClass;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.java.checks.fields.SerialVersionUidUnchanged;
import org.revapi.java.spi.TypeEnvironment;
import org.revapi.java.spi.UseSite;
import org.revapi.java.suid.TestClass;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class SUIDGeneratorTest {

    @SupportedSourceVersion(SourceVersion.RELEASE_7)
    @SupportedAnnotationTypes("java.lang.SuppressWarnings")
    private static class SUIDGeneratingAnnotationProcessor extends AbstractProcessor {
        public long generatedSUID;

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(SuppressWarnings.class);

            if (elements.isEmpty()) {
                return false;
            }

            TypeElement testType = (TypeElement) elements.iterator().next();

            generatedSUID = SerialVersionUidUnchanged.computeSerialVersionUID(testType, new TypeEnvironment() {
                @Nonnull
                @Override
                public Elements getElementUtils() {
                    return processingEnv.getElementUtils();
                }

                @Nonnull
                @Override
                public Types getTypeUtils() {
                    return processingEnv.getTypeUtils();
                }

                @Nullable
                @Override
                public <R, P> R visitUseSites(@Nonnull TypeElement type, @Nonnull UseSite.Visitor<R, P> visitor,
                    @Nullable P parameter) {
                    return null;
                }

                @Nonnull
                @Override
                public Set<TypeElement> getAccessibleSubclasses(@Nonnull TypeElement type) {
                    return Collections.emptySet();
                }

                @Override
                public boolean isExplicitlyIncluded(Element element) {
                    return true;
                }

                @Override
                public boolean isExplicitlyExcluded(Element element) {
                    return false;
                }
            });

            return true;
        }
    }

    @Test
    public void testSUIDGeneration() throws Exception {
        try {
            ObjectStreamClass s = ObjectStreamClass.lookup(TestClass.class);
            long officialSUID = s.getSerialVersionUID();
            SUIDGeneratingAnnotationProcessor ap = new SUIDGeneratingAnnotationProcessor();

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

            JavaCompiler.CompilationTask task = compiler
                .getTask(null, null, null, null, Arrays.asList(TestClass.class.getName()),
                    Arrays.asList(new SourceInClassLoader("suid/TestClass.java")));

            task.setProcessors(Arrays.asList(ap));

            task.call();

            Assert.assertEquals(officialSUID, ap.generatedSUID);
        } finally {
            new File("TestClass.class").delete();
        }
    }
}
