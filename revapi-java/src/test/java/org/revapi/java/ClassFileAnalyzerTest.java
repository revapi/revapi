/*
 * Copyright 2013 Lukas Krejci
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

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

import org.revapi.java.classes.TestAbstractClass;
import org.revapi.java.classes.TestAnnotation;
import org.revapi.java.classes.TestGenericClass;
import org.revapi.java.elements.AnnotationAttributeElement;
import org.revapi.java.elements.AnnotationElement;
import org.revapi.java.elements.ArrayAnnotationValueElement;
import org.revapi.java.elements.ClassAnnotationValueElement;
import org.revapi.java.elements.ClassElement;
import org.revapi.java.elements.ClassTree;
import org.revapi.java.elements.EnumAnnotationValueElement;
import org.revapi.java.elements.FieldElement;
import org.revapi.java.elements.MethodElement;
import org.revapi.java.elements.PrimitiveAnnotationValueElement;

/**
 * @author Lukas Krejci
 * @since 1.0
 */

public class ClassFileAnalyzerTest {

    @Test
    public void testAnnotationParsing() throws Exception {
        ClassFileAnalyzer analyzer = new ClassFileAnalyzer(Util.getArchiveForTestClass(TestAnnotation.class));
        ClassTree tree = analyzer.analyze();

        assertEquals("Exactly one class expected.", tree.getRoots().size(), 1);

        ClassElement cls = tree.getRoots().iterator().next();

        assertEquals(TestAnnotation.class.getName(), cls.getClassName());
        assertEquals(Object.class.getName(), cls.getSuperClass());
        assertEquals(1, cls.getInterfaces().length);
        assertEquals(Annotation.class.getName(), cls.getInterfaces()[0]);

        List<AnnotationElement> annotations = cls.searchChildren(AnnotationElement.class, false, null);
        assertEquals(2, annotations.size());
        Iterator<AnnotationElement> it = annotations.iterator();

        AnnotationElement target = it.next();
        AnnotationElement testAnnotation = it.next();

        assertEquals(Target.class.getName(), target.getClassName());

        ArrayAnnotationValueElement attrValue = target.getAttribute("value").as(ArrayAnnotationValueElement.class);

        assertNotNull(attrValue);

        for (int i = 0; i < 5; ++i) {
            assertEquals(ElementType.class.getName(), attrValue.get(i).as(EnumAnnotationValueElement.class)
                .getClassName());
        }
        assertEquals(ElementType.ANNOTATION_TYPE.name(), attrValue.get(0).as(EnumAnnotationValueElement.class)
            .getConstant());
        assertEquals(ElementType.TYPE.name(), attrValue.get(1).as(EnumAnnotationValueElement.class).getConstant());
        assertEquals(ElementType.FIELD.name(), attrValue.get(2).as(EnumAnnotationValueElement.class)
            .getConstant());
        assertEquals(ElementType.METHOD.name(), attrValue.get(3).as(EnumAnnotationValueElement.class).getConstant());
        assertEquals(ElementType.PARAMETER.name(), attrValue.get(4).as(EnumAnnotationValueElement.class)
            .getConstant());

        assertEquals(TestAnnotation.class.getName(), testAnnotation.getClassName());

        assertEquals(1, testAnnotation.searchChildren(AnnotationAttributeElement.class, false, null).size());
        assertNotNull(testAnnotation.getAttribute("param6"));

        ArrayAnnotationValueElement param6Value = testAnnotation.getAttribute("param6").as(
            ArrayAnnotationValueElement.class);
        assertEquals(Target.class.getName(), param6Value.get(0).as(AnnotationElement.class).getClassName());

        AnnotationElement param6Value_0 = param6Value.get(0).as(AnnotationElement.class);
        AnnotationElement param6Value_1 = param6Value.get(1).as(AnnotationElement.class);
        AnnotationElement param6Value_2 = param6Value.get(2).as(AnnotationElement.class);

        assertEquals(ElementType.class.getName(), param6Value_0.getAttribute("value").as(
            ArrayAnnotationValueElement.class).get(0).as(EnumAnnotationValueElement.class).getClassName());
        assertEquals(ElementType.class.getName(), param6Value_1.getAttribute("value").as(
            ArrayAnnotationValueElement.class).get(0).as(EnumAnnotationValueElement.class).getClassName());
        assertEquals(ElementType.class.getName(), param6Value_2.getAttribute("value").as(
            ArrayAnnotationValueElement.class).get(0).as(EnumAnnotationValueElement.class).getClassName());

        assertEquals(ElementType.ANNOTATION_TYPE.name(),
            param6Value_0.getAttribute("value").as(ArrayAnnotationValueElement.class).get(0)
                .as(EnumAnnotationValueElement.class).getConstant());
        assertEquals(ElementType.CONSTRUCTOR.name(),
            param6Value_1.getAttribute("value").as(ArrayAnnotationValueElement.class).get(0)
                .as(EnumAnnotationValueElement.class).getConstant());
        assertEquals(ElementType.FIELD.name(),
            param6Value_2.getAttribute("value").as(ArrayAnnotationValueElement.class).get(0)
                .as(EnumAnnotationValueElement.class).getConstant());
    }

    @Test
    public void testDefaultValuesForAnnotationMethods() throws Exception {
        ClassFileAnalyzer analyzer = new ClassFileAnalyzer(Util.getArchiveForTestClass(TestAnnotation.class));
        ClassTree tree = analyzer.analyze();

        ClassElement cls = tree.getRoots().iterator().next();

        List<MethodElement> methods = cls.searchChildren(MethodElement.class, false, null);

        assertEquals(6, methods.size());

        MethodElement param1 = methods.get(0);
        MethodElement param2 = methods.get(1);
        MethodElement param3 = methods.get(2);
        MethodElement param4 = methods.get(3);
        MethodElement param5 = methods.get(4);
        MethodElement param6 = methods.get(5);

        assertEquals(int.class.getName(), param1.getReturnType());
        assertEquals(0, param1.getAnnotationMethodDefaultValue().as(PrimitiveAnnotationValueElement.class).getValue());

        assertEquals(Class.class.getName(), param2.getReturnType());
        assertEquals(String.class.getName(),
            param2.getAnnotationMethodDefaultValue().as(ClassAnnotationValueElement.class).getClassName());

        assertEquals(Target.class.getName(), param3.getReturnType());
        AnnotationElement param3Default = param3.getAnnotationMethodDefaultValue().as(AnnotationElement.class);
        assertEquals(Target.class.getName(), param3Default.getClassName());
        assertNotNull(param3Default.getAttribute("value"));
        assertEquals(ElementType.class.getName(),
            param3Default.getAttribute("value").as(ArrayAnnotationValueElement.class).get(0)
                .as(EnumAnnotationValueElement.class).getClassName());
        assertEquals(ElementType.ANNOTATION_TYPE.name(),
            param3Default.getAttribute("value").as(ArrayAnnotationValueElement.class).get(0)
                .as(EnumAnnotationValueElement.class).getConstant());

        assertEquals("int[]", param4.getReturnType());
        assertEquals(2, param4.getAnnotationMethodDefaultValue().as(ArrayAnnotationValueElement.class).getLength());
        assertEquals(1, param4.getAnnotationMethodDefaultValue().as(ArrayAnnotationValueElement.class).get(0)
            .as(PrimitiveAnnotationValueElement.class).getValue());
        assertEquals(2, param4.getAnnotationMethodDefaultValue().as(ArrayAnnotationValueElement.class).get(1)
            .as(PrimitiveAnnotationValueElement.class).getValue());

        assertEquals("java.lang.String[]", param5.getReturnType());
        assertEquals(2, param5.getAnnotationMethodDefaultValue().as(ArrayAnnotationValueElement.class).getLength());
        assertEquals("1", param5.getAnnotationMethodDefaultValue().as(ArrayAnnotationValueElement.class).get(0)
            .as(PrimitiveAnnotationValueElement.class).getValue());
        assertEquals("2", param5.getAnnotationMethodDefaultValue().as(ArrayAnnotationValueElement.class).get(1)
            .as(PrimitiveAnnotationValueElement.class).getValue());

        assertEquals("java.lang.annotation.Target[]", param6.getReturnType());
        assertNull(param6.getAnnotationMethodDefaultValue());
    }

    @Test
    public void testFieldParsing() throws Exception {
        ClassFileAnalyzer analyzer = new ClassFileAnalyzer(Util.getArchiveForTestClass(TestAbstractClass.class));

        ClassTree tree = analyzer.analyze();

        ClassElement cls = tree.getRoots().iterator().next();

        List<FieldElement> fields = cls.searchChildren(FieldElement.class, false, null);
        assertEquals(3, fields.size());

        Iterator<FieldElement> it = fields.iterator();

        FieldElement field1 = it.next();
        FieldElement field2 = it.next();

        System.out.println(cls);
    }

    @Test
    public void testMethodParsing() throws Exception {
        ClassFileAnalyzer analyzer = new ClassFileAnalyzer(Util.getArchiveForTestClass(TestAbstractClass.class));

        ClassTree tree = analyzer.analyze();

        ClassElement cls = tree.getRoots().iterator().next();

        System.out.println(cls);
    }

    @Test
    public void testGenericSignatures() throws Exception {
        ClassFileAnalyzer analyzer = new ClassFileAnalyzer(Util.getArchiveForTestClass(TestGenericClass.class));

        ClassTree tree = analyzer.analyze();

        ClassElement cls = tree.getRoots().iterator().next();

        System.out.println(cls);
    }
}
