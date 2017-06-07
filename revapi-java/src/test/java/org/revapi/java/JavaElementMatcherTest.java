package org.revapi.java;

import static org.junit.Assert.assertTrue;

import javax.lang.model.element.ElementKind;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.java.compilation.InclusionFilter;
import org.revapi.java.matcher.JavaElementMatcher;
import org.revapi.java.model.FieldElement;
import org.revapi.java.model.JavaElementForest;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.MethodParameterElement;
import org.revapi.java.model.TypeElement;
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
            "package,'element.matcher'",
            "package,/element\\.matche[rc]/",
            "class,'element.matcher.MatchByKind.Klass'",
            "class,/element\\.matcher\\.MatchByKind\\.Klass/",
            "name,'Klass'",
            "name,/[kK]lass/",
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
            "class,'element.matcher.MatchByKind.GenericClass<T extends java.lang.Number>'",
            "signature,'element.matcher.MatchByKind.GenericClass<T extends java.lang.Number>'",
            "erasedSignature,'element.matcher.MatchByKind.GenericClass'"
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
            "package,'element.matcher'",
            "package,/element\\.matche[rc]/",
            "class,'element.matcher.MatchByKind.Iface'",
            "class,/element\\.matcher\\.MatchByKind\\.Iface/",
            "name,'Iface'",
            "name,/[iI]face/",
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
            "package,'element.matcher'",
            "package,/element\\.matche[rc]/",
            "class,'element.matcher.MatchByKind.Enm'",
            "class,/element\\.matcher\\.MatchByKind\\.Enm/",
            "name,'Enm'",
            "name,/[eE]nm/",
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
            "package,'element.matcher'",
            "package,/element\\.matche[rc]/",
            "class,'element.matcher.MatchByKind.Enm'",
            "class,/element\\.matcher\\.MatchByKind\\.Enm/",
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
            "package,'element.matcher'",
            "package,/element\\.matche[rc]/",
            "class,'element.matcher.MatchByKind'",
            "class,/element\\.matcher\\.MatchByKind/",
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
            "package,'element.matcher'",
            "package,/element\\.matche[rc]/",
            "class,'element.matcher.MatchByKind'",
            "class,/element\\.matcher\\.MatchByKind/",
            "name,'method'",
            "name,/[mM]ethod/",
            "signature,'<T>void(int)'",
            "signature,/<T>void\\(int\\)/",
            "representation,'method <T> void element.matcher.MatchByKind::method(int)'",
            "returnType,'void'",
            "returnType,/vo.d/"})
    public void testMatchByKind_method(String quality, String expectedValue) throws Exception {
        testSimpleMatchForElementType(MethodElement.class, quality, expectedValue,
                Filter.deep(m -> m.getDeclaringElement().getSimpleName().contentEquals("method")));
    }

    @Test
    @Parameters({
            "kind,'constructor'",
            "kind,/construct.r/",
            "package,'element.matcher'",
            "package,/element\\.matche[rc]/",
            "class,'element.matcher.MatchByKind'",
            "class,/element\\.matcher\\.MatchByKind/",
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
            "package,'element.matcher'",
            "package,/element\\.matche[rc]/",
            "class,'element.matcher.MatchByKind'",
            "class,/element\\.matcher\\.MatchByKind/",
            "name,'arg0'",
            "name,/arg\\d/",
            "signature,'int'",
            "signature,/in[Tt]/",
            "representation,'parameter <T> void element.matcher.MatchByKind::method(===int===)'",
            "index,0"})
    public void testMatchByKind_parameter(String quality, String expectedValue) throws Exception {
        testSimpleMatchForElementType(MethodParameterElement.class, quality, expectedValue,
                Filter.deep(p -> p.getParent().getDeclaringElement().getSimpleName().contentEquals("method")));
    }

    private <T extends Element> void testSimpleMatchForElementType(Class<T> elementType, String quality, String expectedValue, Filter<T> filter) throws Exception {
        testOn(types -> {
            Element cls = types.getRoots().first();
            Element el = cls.searchChildren(elementType, true, filter).get(0);

            assertTrue("Testing [" + quality + " = " + expectedValue + "] on " + el,
                    matcher.matches(quality + " = " + expectedValue, el));
        }, "elementmatcher/MatchByKind.java");
    }

    private void testOn(Consumer<JavaElementForest> test, String... sources) throws Exception {
        ArchiveAndCompilationPath ar = createCompiledJar("test", sources);
        try {
            JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(new API(
                    Arrays.asList(new ShrinkwrapArchive(ar.archive)),
                    null), Executors.newSingleThreadExecutor(), null, false,
                    InclusionFilter.acceptAll());

            JavaElementForest results = analyzer.analyze();

            test.accept(results);
        } finally {
            deleteDir(ar.compilationPath);
        }
    }
}
