/*
 * Copyright 2014-2018 Lukas Krejci
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

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.lang.model.element.ElementKind;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.FilterMatch;
import org.revapi.FilterResult;
import org.revapi.java.matcher.JavaElementMatcher;
import org.revapi.java.model.FieldElement;
import org.revapi.java.model.JavaElementForest;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.MethodParameterElement;
import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.query.Filter;

/**
 * @author Lukas Krejci
 */
@RunWith(JUnitParamsRunner.class)
public class JavaElementMatcherTest extends AbstractJavaElementAnalyzerTest {

    private JavaElementMatcher matcher = new JavaElementMatcher();

    @Before
    public void setup() {
        matcher.initialize(AnalysisContext.builder().build());
    }

    @Test
    @Parameters({
            "kind,'class'",
            "kind,/cl.s{2}/",
            "name,'element.matcher.MatchByKind.Klass'",
            "name,/element\\.matcher\\.MatchByKind\\.Klass/",
            "simpleName,'Klass'",
            "simpleName,/[kK]lass/",
            "signature,'element.matcher.MatchByKind.Klass'",
            "signature,/element\\.matcher\\.MatchByKind\\.Klass/",
            "representation,'class element.matcher.MatchByKind.Klass'"})
    public void testMatch_class(String quality, String expectedValue) throws Exception {
        testSimpleMatchForElementType(TypeElement.class, quality, expectedValue,
                Filter.shallow(t -> t.getDeclaringElement().getKind() == ElementKind.CLASS
                        && "element.matcher.MatchByKind.Klass".equals(t.getCanonicalName())));
    }

    @Test
    @Parameters({
            "name,'element.matcher.MatchByKind.GenericClass'",
            "signature,'element.matcher.MatchByKind.GenericClass<T extends java.lang.Number>'",
            "erased signature,'element.matcher.MatchByKind.GenericClass'"
    })
    public void testMatch_genericClass(String quality, String expectedValue) throws Exception {
        testSimpleMatchForElementType(TypeElement.class, quality, expectedValue,
                Filter.shallow(t -> t.getDeclaringElement().getKind() == ElementKind.CLASS
                        && "element.matcher.MatchByKind.GenericClass".equals(t.getCanonicalName())));

    }

    @Test
    @Parameters({
            "kind,'interface'",
            "kind,/int.[rR]face/",
            "name,'element.matcher.MatchByKind.Iface'",
            "name,/element\\.matcher\\.MatchByKind\\.Iface/",
            "simpleName,'Iface'",
            "simpleName,/[iI]face/",
            "signature,'element.matcher.MatchByKind.Iface'",
            "signature,/element\\.matcher\\.MatchByKind\\.Iface/",
            "representation,'interface element.matcher.MatchByKind.Iface'"})
    public void testMatchByKind_interface(String quality, String expectedValue) throws Exception {
        testSimpleMatchForElementType(TypeElement.class, quality, expectedValue,
                Filter.shallow(t -> t.getDeclaringElement().getKind() == ElementKind.INTERFACE));
    }

    @Test
    @Parameters({
            "kind,'enum'",
            "kind,/en.m/",
            "name,'element.matcher.MatchByKind.Enm'",
            "name,/element\\.matcher\\.MatchByKind\\.Enm/",
            "simpleName,'Enm'",
            "simpleName,/[eE]nm/",
            "signature,'element.matcher.MatchByKind.Enm'",
            "signature,/element\\.matcher\\.MatchByKind\\.Enm/",
            "representation,'enum element.matcher.MatchByKind.Enm'"})
    public void testMatchByKind_enum(String quality, String expectedValue) throws Exception {
        testSimpleMatchForElementType(TypeElement.class, quality, expectedValue,
                Filter.shallow(t -> t.getDeclaringElement().getKind() == ElementKind.ENUM));
    }

    @Test
    @Parameters({
            "kind,'enumConstant'",
            "kind,/en.m.+/",
            "name,'CONSTANT'",
            "name,/[cC]ONSTANT/",
            "signature,'element.matcher.MatchByKind.Enm'",
            "signature,/element\\.matcher\\.MatchByKind\\.Enm/",
            "representation,'field element.matcher.MatchByKind.Enm.CONSTANT'"})
    public void testMatchByKind_enumConstant(String quality, String expectedValue) throws Exception {
        testSimpleMatchForElementType(FieldElement.class, quality, expectedValue,
                Filter.deep(t -> t.getDeclaringElement().getKind() == ElementKind.ENUM_CONSTANT));
    }

    @Test
    @Parameters({
            "kind,'field'",
            "kind,/f.eld/",
            "name,'field'",
            "name,/[fF]ield/",
            "signature,'int'",
            "signature,/int/",
            "representation,'field element.matcher.MatchByKind.field'"})
    public void testMatchByKind_field(String quality, String expectedValue) throws Exception {
        testSimpleMatchForElementType(FieldElement.class, quality, expectedValue,
                Filter.shallow(t -> t.getDeclaringElement().getKind() != ElementKind.ENUM_CONSTANT));
    }

    @Test
    @Parameters({
            "kind,'method'",
            "kind,/m.thod/",
            "name,'method'",
            "name,/[mM]ethod/",
            "signature,'<T>void(int)'",
            "signature,/<T>void\\(int\\)/",
            "generic signature,'<T>void(int)'",
            "erased signature,'void(int)'",
            "representation,'method <T> void element.matcher.MatchByKind::method(int)'"})
    public void testMatchByKind_method(String quality, String expectedValue) throws Exception {
        testSimpleMatchForElementType(MethodElement.class, quality, expectedValue,
                Filter.deep(m -> m.getDeclaringElement().getSimpleName().contentEquals("method")));
    }

    @Test
    @Parameters({
            "kind,'constructor'",
            "kind,/construct.r/",
            "name,'<init>'",
            "name,/<[iI]nit>/",
            "signature,'void()'",
            "signature,/void\\(\\)/",
            "representation,'method void element.matcher.MatchByKind::<init>()'"})
    public void testMatchByKind_constructor(String quality, String expectedValue) throws Exception {
        testSimpleMatchForElementType(MethodElement.class, quality, expectedValue,
                Filter.shallow(MethodElement::isConstructor));
    }

    @Test
    @Parameters({
            "kind,'parameter'",
            "kind,/para[mM]eter/",
            "name,'arg0'",
            "name,/arg\\d/",
            "signature,'int'",
            "signature,/in[Tt]/",
            "representation,'parameter <T> void element.matcher.MatchByKind::method(===int===)'"})
    public void testMatchByKind_parameter(String quality, String expectedValue) throws Exception {
        testSimpleMatchForElementType(MethodParameterElement.class, quality, expectedValue,
                Filter.deep(p -> p.getParent().getDeclaringElement().getSimpleName().contentEquals("method")));
    }

    @Test
    public void testThrowsExpression() throws Exception {
        testOn("elementmatcher/Throws.java", types -> {
            Element cls = types.getRoots().first();
            Function<String, JavaMethodElement> getMethod = name -> cls.searchChildren(JavaMethodElement.class, false,
                    Filter.shallow(m -> m.getDeclaringElement().getSimpleName().contentEquals(name))).get(0);

            JavaMethodElement noThrow = getMethod.apply("noThrow");
            JavaMethodElement singleThrow = getMethod.apply("singleThrow");
            JavaMethodElement multipleThrow = getMethod.apply("multipleThrow");

            String doesntThrowTest = "doesn't throw";
            String throwsSingleTest = "throws 'java.lang.RuntimeException' and doesn't throw 'kachny'";
            String throwsMultipleTest = "throws 'java.lang.RuntimeException' and throws /.*IOException/ and doesn't throw 'kachny'";

            assertMatches(doesntThrowTest, noThrow);
            assertDoesntMatch(throwsSingleTest, noThrow);
            assertDoesntMatch(throwsMultipleTest, noThrow);

            assertDoesntMatch(doesntThrowTest, singleThrow);
            assertMatches(throwsSingleTest, singleThrow);
            assertDoesntMatch(throwsMultipleTest, singleThrow);

            assertDoesntMatch(doesntThrowTest, multipleThrow);
            assertMatches(throwsSingleTest, multipleThrow);
            assertMatches(throwsMultipleTest, multipleThrow);
        });
    }

    @Test
    public void testSubTypeExpression() throws Exception {
        testOn("elementmatcher/SubType.java", types -> {
            Element top = types.getRoots().first();

            Function<String, JavaTypeElement> getType = name -> top.searchChildren(JavaTypeElement.class, false,
                    Filter.shallow(m -> m.getDeclaringElement().getSimpleName().contentEquals(name))).get(0);

            JavaTypeElement base = getType.apply("Base");
            JavaTypeElement child1 = getType.apply("Child1");
            JavaTypeElement child2 = getType.apply("Child2");
            JavaTypeElement grandChild = getType.apply("GrandChild");

            String directlyExtendsTest = "directly extends 'element.matcher.SubType.Base'";
            String extendsTest = "extends 'element.matcher.SubType.Base'";
            String directlyImplementsTest = "directly implements 'element.matcher.SubType.Iface'";
            String implementsTest = "implements 'element.matcher.SubType.Iface'";
            String doesntDirectlyExtendTest = "doesn't directly extend 'element.matcher.SubType.Child1'";

            assertDoesntMatch(directlyExtendsTest, base);
            assertMatches(directlyExtendsTest, child1);
            assertMatches(directlyExtendsTest, child2);
            assertDoesntMatch(directlyExtendsTest, grandChild);

            assertDoesntMatch(extendsTest, base);
            assertMatches(extendsTest, child1);
            assertMatches(extendsTest, child2);
            assertMatches(extendsTest, grandChild);

            assertDoesntMatch(directlyImplementsTest, base);
            assertMatches(directlyImplementsTest, child1);
            assertDoesntMatch(directlyImplementsTest, child2);
            assertDoesntMatch(directlyImplementsTest, grandChild);

            assertDoesntMatch(implementsTest, base);
            assertMatches(implementsTest, child1);
            assertDoesntMatch(implementsTest, child2);
            assertMatches(implementsTest, grandChild);

            assertMatches(doesntDirectlyExtendTest, base);
            assertMatches(doesntDirectlyExtendTest, child1);
            assertMatches(doesntDirectlyExtendTest, child2);
            assertDoesntMatch(doesntDirectlyExtendTest, grandChild);
        });
    }

    @Test
    public void testOverridesExpression() throws Exception {
        testOn("elementmatcher/Overrides.java", types -> {
            Element top = types.getRoots().first();
            JavaTypeElement check = top.searchChildren(JavaTypeElement.class, false,
                    Filter.shallow(t -> t.getDeclaringElement().getSimpleName().contentEquals("Check"))).get(0);

            Function<String, JavaMethodElement> getMethod = methodName ->
                    check.searchChildren(JavaMethodElement.class, true,
                            Filter.deep(m -> m.getDeclaringElement().getSimpleName().contentEquals(methodName))).get(0);

            JavaMethodElement baseOverride = getMethod.apply("baseMethod");
            JavaMethodElement ifaceOverride = getMethod.apply("ifaceMethod");
            JavaMethodElement myMethod = getMethod.apply("myMethod");

            String overridesBase = "overrides 'java.lang.Number element.matcher.Overrides.Base::baseMethod(int)'";
            String overridesIface = "overrides 'void element.matcher.Overrides.Iface::ifaceMethod(java.lang.String)'";
            String doesntOverrideBase = "doesn't override 'java.lang.Number element.matcher.Overrides.Base::baseMethod(int)'";
            String doesntOverrideIface = "doesn't override 'void element.matcher.Overrides.Iface::ifaceMethod(java.lang.String)'";
            String doesntOverride = "doesn't override";

            assertMatches(overridesBase, baseOverride);
            assertDoesntMatch(overridesBase, ifaceOverride);
            assertDoesntMatch(overridesBase, myMethod);

            assertDoesntMatch(overridesIface, baseOverride);
            assertMatches(overridesIface, ifaceOverride);
            assertDoesntMatch(overridesIface, myMethod);

            assertDoesntMatch(doesntOverrideBase, baseOverride);
            assertMatches(doesntOverrideBase, ifaceOverride);
            assertMatches(doesntOverrideBase, myMethod);

            assertMatches(doesntOverrideIface, baseOverride);
            assertDoesntMatch(doesntOverrideIface, ifaceOverride);
            assertMatches(doesntOverrideIface, myMethod);

            assertDoesntMatch(doesntOverride, baseOverride);
            assertDoesntMatch(doesntOverride, ifaceOverride);
            assertMatches(doesntOverride, myMethod);
        });
    }

    @Test
    public void testReturnsExpression() throws Exception {
        testOn("elementmatcher/Returns.java", types -> {
            Element top = types.getRoots().first();
            Function<String, JavaMethodElement> getMethod = methodName ->
                    top.searchChildren(JavaMethodElement.class, true,
                            Filter.deep(m -> m.getDeclaringElement().getSimpleName().contentEquals(methodName))).get(0);

            JavaMethodElement objectMethod = getMethod.apply("objectMethod");
            JavaMethodElement voidMethod = getMethod.apply("voidMethod");
            JavaMethodElement intMethod = getMethod.apply("intMethod");
            JavaMethodElement stringMethod = getMethod.apply("stringMethod");

            String returnsObject = "returns 'java.lang.Object'";
            String returnsPreciselyObject = "returns precisely 'java.lang.Object'";
            String returnsVoid = "returns 'void'";
            String returnsInt = "returns 'int'";
            String returnsString = "returns 'java.lang.String'";

            assertMatches(returnsObject, objectMethod);
            assertDoesntMatch(returnsObject, voidMethod);
            assertDoesntMatch(returnsObject, intMethod);
            assertMatches(returnsObject, stringMethod);

            assertMatches(returnsPreciselyObject, objectMethod);
            assertDoesntMatch(returnsPreciselyObject, voidMethod);
            assertDoesntMatch(returnsPreciselyObject, intMethod);
            assertDoesntMatch(returnsPreciselyObject, stringMethod);

            assertDoesntMatch(returnsVoid, objectMethod);
            assertMatches(returnsVoid, voidMethod);
            assertDoesntMatch(returnsVoid, intMethod);
            assertDoesntMatch(returnsVoid, stringMethod);

            assertDoesntMatch(returnsInt, objectMethod);
            assertDoesntMatch(returnsInt, voidMethod);
            assertMatches(returnsInt, intMethod);
            assertDoesntMatch(returnsInt, stringMethod);

            assertDoesntMatch(returnsString, objectMethod);
            assertDoesntMatch(returnsString, voidMethod);
            assertDoesntMatch(returnsString, intMethod);
            assertMatches(returnsString, stringMethod);
        });
    }

    @Test
    public void testNumberOfArguments() throws Exception {
        testOn("elementmatcher/Arguments.java", types -> {
            Element cls = types.getRoots().first();

            Function<String, MethodElement> getMethod = methodName ->
                    cls.searchChildren(MethodElement.class, false,
                            Filter.shallow(m -> m.getDeclaringElement().getSimpleName().contentEquals(methodName)))
                            .get(0);

            MethodElement method0 = getMethod.apply("method0");
            MethodElement method1 = getMethod.apply("method1");
            MethodElement method2 = getMethod.apply("method2");
            MethodElement method3 = getMethod.apply("method3");

            String hasNoArgs = "has 0 arguments";
            String hasLessThan2Args = "has less than 2 arguments";
            String hasMoreThan1Args = "has more than 1 arguments";
            String doesntHave3Args = "doesn't have 3 arguments";
            String doesntHaveMoreThan1Arg = "doesn't have more than 1 arguments";

            assertMatches(hasNoArgs, method0);
            assertDoesntMatch(hasNoArgs, method1);
            assertDoesntMatch(hasNoArgs, method2);
            assertDoesntMatch(hasNoArgs, method3);

            assertMatches(hasLessThan2Args, method0);
            assertMatches(hasLessThan2Args, method1);
            assertDoesntMatch(hasLessThan2Args, method2);
            assertDoesntMatch(hasLessThan2Args, method3);

            assertDoesntMatch(hasMoreThan1Args, method0);
            assertDoesntMatch(hasMoreThan1Args, method1);
            assertMatches(hasMoreThan1Args, method2);
            assertMatches(hasMoreThan1Args, method3);

            assertMatches(doesntHave3Args, method0);
            assertMatches(doesntHave3Args, method1);
            assertMatches(doesntHave3Args, method2);
            assertDoesntMatch(doesntHave3Args, method3);

            assertMatches(doesntHaveMoreThan1Arg, method0);
            assertMatches(doesntHaveMoreThan1Arg, method1);
            assertDoesntMatch(doesntHaveMoreThan1Arg, method2);
            assertDoesntMatch(doesntHaveMoreThan1Arg, method3);
        });
    }

    @Test
    public void testArgumentIndex() throws Exception {
        testOn("elementmatcher/Arguments.java", types -> {
            Element cls = types.getRoots().first();

            Function<String, MethodElement> getMethod = methodName ->
                    cls.searchChildren(MethodElement.class, false,
                            Filter.shallow(m -> m.getDeclaringElement().getSimpleName().contentEquals(methodName)))
                            .get(0);

            MethodElement method0 = getMethod.apply("method0");
            MethodElement method1 = getMethod.apply("method1");
            MethodElement method2 = getMethod.apply("method2");
            MethodElement method3 = getMethod.apply("method3");

            String firstArgIsInt = "has argument (has index 0 and has signature 'int')";
            String secondArgLong = "has argument (has index 1 and has signature 'long')";
            String firstArgNotLong = "doesn't have argument (has index 0 and has signature 'long')";

            assertDoesntMatch(firstArgIsInt, method0);
            assertMatches(firstArgIsInt, method1);
            assertMatches(firstArgIsInt, method2);
            assertMatches(firstArgIsInt, method3);

            assertDoesntMatch(secondArgLong, method0);
            assertDoesntMatch(secondArgLong, method1);
            assertMatches(secondArgLong, method2);
            assertMatches(secondArgLong, method3);

            assertMatches(firstArgNotLong, method0);
            assertMatches(firstArgNotLong, method1);
            assertMatches(firstArgNotLong, method2);
            assertMatches(firstArgNotLong, method3);
        });
    }

    @Test
    public void testHasArgument() throws Exception {
        testOn("elementmatcher/Arguments.java", types -> {
            Element cls = types.getRoots().first();

            Function<String, MethodElement> getMethod = methodName ->
                    cls.searchChildren(MethodElement.class, false,
                            Filter.shallow(m -> m.getDeclaringElement().getSimpleName().contentEquals(methodName)))
                            .get(0);

            MethodElement method0 = getMethod.apply("method0");
            MethodElement method1 = getMethod.apply("method1");
            MethodElement method2 = getMethod.apply("method2");
            MethodElement method3 = getMethod.apply("method3");

            String hasFirstIntArg = "has argument 1 'int'";
            String hasStringArg = "has argument 'java.lang.String'";
            String doesntHaveStringArg = "doesn't have argument 'java.lang.String'";
            String hasThirdFloatArg = "has argument 3 'float'";

            assertDoesntMatch(hasFirstIntArg, method0);
            assertMatches(hasFirstIntArg, method1);
            assertMatches(hasFirstIntArg, method2);
            assertMatches(hasFirstIntArg, method3);

            assertDoesntMatch(hasStringArg, method0);
            assertDoesntMatch(hasStringArg, method1);
            assertDoesntMatch(hasStringArg, method2);
            assertDoesntMatch(hasStringArg, method3);

            assertMatches(doesntHaveStringArg, method0);
            assertMatches(doesntHaveStringArg, method1);
            assertMatches(doesntHaveStringArg, method2);
            assertMatches(doesntHaveStringArg, method3);

            assertDoesntMatch(hasThirdFloatArg, method0);
            assertDoesntMatch(hasThirdFloatArg, method1);
            assertDoesntMatch(hasThirdFloatArg, method2);
            assertMatches(hasThirdFloatArg, method3);
        });
    }

    @Test
    public void testHasAnnotation() throws Exception {
        testOn("elementmatcher/Annotations.java", types -> {
            Element cls = types.getRoots().first();

            Function<String, MethodElement> getMethod = methodName ->
                    cls.searchChildren(MethodElement.class, false,
                            Filter.shallow(m -> m.getDeclaringElement().getSimpleName().contentEquals(methodName)))
                            .get(0);
            Function<String, TypeElement> getClass = typeName ->
                    cls.searchChildren(TypeElement.class, false,
                            Filter.shallow(m -> m.getDeclaringElement().getSimpleName().contentEquals(typeName)))
                            .get(0);

            MethodElement method1 = getMethod.apply("method1");
            MethodElement method2 = getMethod.apply("method2");
            MethodElement method3 = getMethod.apply("method3");
            MethodElement method4 = getMethod.apply("method4");

            TypeElement base = getClass.apply("Base");
            TypeElement iface = getClass.apply("Iface");
            TypeElement inheritingChild = getClass.apply("InheritingChild");
            TypeElement notInheritingChild = getClass.apply("NotInheritingChild");

            String hasAnnotation = "has annotation '@element.matcher.Annotations.A'";
            String hasDeclaredInheritableAnnotation = "has declared annotation '@element.matcher.Annotations.B'";
            String hasInheritableAnnotation = "has annotation '@element.matcher.Annotations.B'";

            assertMatches(hasAnnotation, method1);
            assertDoesntMatch(hasAnnotation, method2);
            assertMatches(hasAnnotation, method3);
            assertMatches(hasAnnotation, method4);

            assertMatches(hasDeclaredInheritableAnnotation, base);
            assertMatches(hasDeclaredInheritableAnnotation, iface);
            assertDoesntMatch(hasDeclaredInheritableAnnotation, inheritingChild);
            assertDoesntMatch(hasDeclaredInheritableAnnotation, notInheritingChild);

            assertMatches(hasInheritableAnnotation, base);
            assertMatches(hasInheritableAnnotation, iface);
            assertMatches(hasInheritableAnnotation, inheritingChild);
            assertDoesntMatch(hasInheritableAnnotation, notInheritingChild);
        });
    }

    @Test
    @Parameters({
            "has explicit attribute 'value',false,true,false,true",
            "has attribute 'value' that is equal to 'kachna',true,true,false,false",
            "has attribute 'arg2' that is not equal to 0,true,false,true,false",
            "has attribute 'value' that is equal to {'kachna'\\,/dra.hma/},false,false,false,true",
            "has attribute 'arg3' that is equal to {'@element.matcher.Annotations.B'},false,false,false,true",
            "has attribute 'arg2' that is greater than 0,true,false,true,false",
            "has attribute 'arg2' that is less than 1,false,false,false,true",
            "has attribute that has 2 elements,false,false,false,true",
            "has attribute that has more than 1 elements,false,false,false,true",
            "has explicit attribute that has less than 3 elements,false,false,false,true",
            "has attribute that has element that is equal to 'kachna',false,false,false,true",
            "has attribute that has element that (is equal to 'kachna'),false,false,false,true",
            "has attribute that has element 1,false,false,false,true",
            "has attribute that has element 1 that is not equal to 'kachna',false,false,false,true",
            "has attribute 'value' that doesn't have element 1,true,true,true,false",
            "has attribute 'value' that doesn't have element 0 that is equal to 'kachna',true,true,true,false",
            "has attribute that has element that has attribute that is equal to 'kachna',false,false,false,true",
            "has explicit attribute that is equal to 0 or is equal to 'kachna',false,true,false,true",
            "has explicit attribute that has type 'element.matcher.Annotations.B[]',false,false,false,true",
            "has explicit attribute that doesn't have type 'element.matcher.Annotations.B[]',true,true,true,true"
    })
    public void testHasAnnotationAttribute(String test, boolean matchesMethod1, boolean matchesMethod2,
                                           boolean matchesMethod3, boolean matchesMethod4) throws Exception {
        testOn("elementmatcher/Annotations.java", types -> {
            Element cls = types.getRoots().first();

            Function<String, MethodElement> getMethod = methodName ->
                    cls.searchChildren(MethodElement.class, false,
                            Filter.shallow(m -> m.getDeclaringElement().getSimpleName().contentEquals(methodName)))
                            .get(0);

            MethodElement method1 = getMethod.apply("method1");
            MethodElement method2 = getMethod.apply("method2");
            MethodElement method3 = getMethod.apply("method3");
            MethodElement method4 = getMethod.apply("method4");

            String realTest = "has annotation (" + test + ")";

            if (matchesMethod1) {
                assertMatches(realTest, method1);
            } else {
                assertDoesntMatch(realTest, method1);
            }

            if (matchesMethod2) {
                assertMatches(realTest, method2);
            } else {
                assertDoesntMatch(realTest, method2);
            }

            if (matchesMethod3) {
                assertMatches(realTest, method3);
            } else {
                assertDoesntMatch(realTest, method3);
            }

            if (matchesMethod4) {
                assertMatches(realTest, method4);
            } else {
                assertDoesntMatch(realTest, method4);
            }
        });
    }

    @Test
    public void testHasMethod() throws Exception {
        //TODO Implement
    }

    @Test
    public void testHasField() throws Exception {
        //TODO Implement
    }

    @Test
    public void testHasOuterClass() throws Exception {
        //TODO Implement
    }

    @Test
    public void testHasInnerClass() throws Exception {
        //TODO Implement
    }

    @Test
    public void testHasSuperType() throws Exception {
        //TODO Implement
    }

    @Test
    public void testHasType() throws Exception {
        //TODO Implement
    }

    @Test
    public void testIsAKind() throws Exception {
        testOn("elementmatcher/MatchByKind.java", types -> {
            Element cls = types.getRoots().first();

            assertMatches("is a 'class'", cls);
            assertMatches("is not a 'method'", cls);
            assertMatches("isn't a 'field'", cls);
        });
    }

    @Test
    public void testInPackage() throws Exception {
        testOn("elementmatcher/MatchByKind.java", types -> {
            Element cls = types.getRoots().first();

            assertMatches("is in package 'element.matcher'", cls);
            assertMatches("is not in package 'java.lang'", cls);
            assertMatches("isn't in package 'java.util'", cls);

            FieldElement enumConst = cls.searchChildren(FieldElement.class, true,
                    Filter.deep(e -> e.getDeclaringElement().getKind() == ElementKind.ENUM_CONSTANT)).get(0);

            //try that nested elements still get package detected
            assertMatches("is in package 'element.matcher'", enumConst);
            assertMatches("is not in package 'java.lang'", enumConst);
            assertMatches("isn't in package 'java.util'", enumConst);
        });
    }

    @Test
    public void testTypeParameters() throws Exception {
        testOn("elementmatcher/TypeParameters.java", types -> {
            Element top = types.getRoots().first();
            JavaTypeElement base = top.searchChildren(JavaTypeElement.class, false,
                    Filter.shallow(t -> "Base".equals(t.getDeclaringElement().getSimpleName().toString()))).get(0);
            JavaTypeElement concreteChild = top.searchChildren(JavaTypeElement.class, false,
                    Filter.shallow(t -> "ConcreteChild".equals(t.getDeclaringElement().getSimpleName().toString()))).get(0);
            JavaTypeElement genericChild = top.searchChildren(JavaTypeElement.class, false,
                    Filter.shallow(t -> "GenericChild".equals(t.getDeclaringElement().getSimpleName().toString()))).get(0);

            assertMatches("has 1 typeParameters", base);
            assertMatches("doesn't have more than 0 typeParameters", concreteChild);
            assertMatches("has 2 typeParameters", genericChild);

            assertDoesntMatch("has typeParameter (has upper bound that extends 'java.lang.String')", base);
            assertDoesntMatch("has typeParameter (is a 'class')", concreteChild);
            assertMatches("has typeParameter that has upper bound 'java.lang.String'", genericChild);
        });
    }

    private void assertMatches(String test, Element element) {
        assertTrue("Expecting match for [" + test + "] on " + element,
                matcher.compile(test)
                        .map(r -> r.test(element))
                        .map(m -> m == FilterMatch.MATCHES)
                        .orElse(false));
    }

    private void assertDoesntMatch(String test, Element element) {
        assertTrue("Expecting no match for [" + test + "] on " + element,
                matcher.compile(test)
                        .map(r -> r.test(element))
                        .map(m -> m == FilterMatch.DOESNT_MATCH)
                        .orElse(false));
    }

    private <T extends Element> void testSimpleMatchForElementType(Class<T> elementType, String quality, String expectedValue, Filter<T> filter) throws Exception {
        testOn("elementmatcher/MatchByKind.java", types -> {
            Element cls = types.getRoots().first();
            Element el = cls.searchChildren(elementType, true, filter).get(0);

            assertTrue("Testing [" + quality + " = " + expectedValue + "] on " + el,
                    matcher.compile("has " + quality + expectedValue)
                            .map(r -> r.test(el))
                            .map(m -> m == FilterMatch.MATCHES)
                            .orElse(false));
        });
    }

    private void testOn(String source, Consumer<JavaElementForest> test) throws Exception {
        ArchiveAndCompilationPath ar = createCompiledJar("test", source);
        try {
            JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(new API(
                    Arrays.asList(new ShrinkwrapArchive(ar.archive)),
                    null), Executors.newSingleThreadExecutor(), null, false
            );

            JavaElementForest results = analyzer.analyze(e -> FilterResult.matchAndDescend());

            test.accept(results);
        } finally {
            deleteDir(ar.compilationPath);
        }
    }
}
