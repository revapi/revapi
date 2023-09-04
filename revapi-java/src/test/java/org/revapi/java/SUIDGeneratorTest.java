/*
 * Copyright 2014-2023 Lukas Krejci
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
package org.revapi.java;

import java.io.File;
import java.io.ObjectStreamClass;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Set;

import javax.annotation.Nonnull;
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
import org.revapi.java.checks.common.SerializationChecker;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.TypeEnvironment;
import org.revapi.java.suid.Empty;
import org.revapi.java.suid.TestClass;

/**
 * @author Lukas Krejci
 *
 * @since 0.1
 */
public class SUIDGeneratorTest {

    @SupportedSourceVersion(SourceVersion.RELEASE_7)
    @SupportedAnnotationTypes("java.lang.SuppressWarnings")
    private static class SUIDGeneratingAnnotationProcessor extends AbstractProcessor {
        public long generatedSUID;
        public long generatedStructuralId;

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(SuppressWarnings.class);

            if (elements.isEmpty()) {
                return false;
            }

            TypeElement testType = (TypeElement) elements.iterator().next();

            TypeEnvironment fakeEnv = new TypeEnvironment() {
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

                @Override
                public JavaTypeElement getModelElement(TypeElement e) {
                    return null;
                }
            };

            generatedSUID = SerializationChecker.computeSerialVersionUID(testType, fakeEnv);
            generatedStructuralId = SerializationChecker.computeStructuralId(testType, fakeEnv);
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

            JavaCompiler.CompilationTask task = compiler.getTask(null, null, null, null,
                    Arrays.asList(TestClass.class.getName()),
                    Arrays.asList(new SourceInClassLoader("suid/TestClass.java")));

            task.setProcessors(Arrays.asList(ap));

            task.call();

            Assert.assertEquals(officialSUID, ap.generatedSUID);
        } finally {
            new File("TestClass.class").delete();
        }
    }

    @Test
    public void testHandlingEmptyClass() throws Exception {
        try {
            ObjectStreamClass s = ObjectStreamClass.lookup(Empty.class);
            long officialSUID = s.getSerialVersionUID();
            SUIDGeneratingAnnotationProcessor ap = new SUIDGeneratingAnnotationProcessor();

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

            JavaCompiler.CompilationTask task = compiler.getTask(null, null, null, null,
                    Arrays.asList(TestClass.class.getName()),
                    Arrays.asList(new SourceInClassLoader("suid/Empty.java")));

            task.setProcessors(Arrays.asList(ap));

            task.call();

            Assert.assertEquals(officialSUID, ap.generatedSUID);

            MessageDigest md = MessageDigest.getInstance("SHA");
            byte[] hashBytes = md.digest("".getBytes("UTF-8"));
            long hash = 0;
            for (int i = Math.min(hashBytes.length, 8) - 1; i >= 0; i--) {
                hash = (hash << 8) | (hashBytes[i] & 0xFF);
            }

            Assert.assertEquals(hash, ap.generatedStructuralId);
        } finally {
            new File("Empty.class").delete();
        }
    }
}
