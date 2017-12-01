package org.revapi.java.compilation;

import java.util.HashMap;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.util.ElementFilter;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.revapi.API;
import org.revapi.Archive;
import org.revapi.java.AbstractJavaElementAnalyzerTest;
import org.revapi.java.model.JavaElementForest;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.UseSite;
import org.revapi.java.test.support.Jar;
import org.revapi.simple.FileArchive;

//This needs to be in this package so that it has access to package private method in ProbingEnvironment
public class UseSiteUpdatingTreeSetTest extends AbstractJavaElementAnalyzerTest {

    @Rule
    public Jar jar = new Jar();

    private JavaElementForest forest;
    private TypeElement A;
    private TypeElement B;
    private TypeElement C;
    private MethodElement Cctor;
    private MethodElement Cm;
    private TypeElement E;
    private MethodElement Ector;

    private void createEnvironment() throws Exception {
        API fakeApi = API.builder().build();
        Archive archive = new FileArchive(null);

        ProbingEnvironment environment = new ProbingEnvironment(fakeApi);
        HashMap<javax.lang.model.element.TypeElement, TypeElement> typeMap = new HashMap<>();

        Jar.Environment env = jar.from().classPathSources("/usesites/", "A.java", "B.java", "C.java", "E.java").build().analyze();
        environment.setProcessingEnvironment(env.processingEnvironment());

        forest = environment.getTree();

        javax.lang.model.element.TypeElement type = env.elements().getTypeElement("A");
        TypeElement model = new TypeElement(environment, archive, type, (DeclaredType) type.asType());
        forest.getRoots().add(model);
        A = model;

        type = env.elements().getTypeElement("B");
        model = new TypeElement(environment, archive, type, (DeclaredType) type.asType());
        forest.getRoots().add(model);
        B = model;

        type = env.elements().getTypeElement("C");
        model = new TypeElement(environment, archive, type, (DeclaredType) type.asType());
        forest.getRoots().add(model);
        C = model;
        ExecutableElement method = ElementFilter.constructorsIn(type.getEnclosedElements()).get(0);
        MethodElement methodModel = new MethodElement(environment, archive, method, (ExecutableType) method.asType());
        model.getChildren().add(methodModel);
        Cctor = methodModel;
        method = ElementFilter.methodsIn(type.getEnclosedElements()).get(0);
        methodModel = new MethodElement(environment, archive, method, (ExecutableType) method.asType());
        model.getChildren().add(methodModel);
        Cm = methodModel;

        type = env.elements().getTypeElement("E");
        model = new TypeElement(environment, archive, type, (DeclaredType) type.asType());
        forest.getRoots().add(model);
        method = ElementFilter.constructorsIn(type.getEnclosedElements()).get(0);
        methodModel = new MethodElement(environment, archive, method, (ExecutableType) method.asType());
        model.getChildren().add(methodModel);
        E = model;
        Ector = methodModel;

        //k now we have all the elements in our model, but we still need to add the use sites
        A.getUseSites().add(new UseSite(UseSite.Type.ANNOTATES, B));
        B.getUseSites().add(new UseSite(UseSite.Type.IS_IMPLEMENTED, C));
        B.getUseSites().add(new UseSite(UseSite.Type.PARAMETER_TYPE, Cm));
        B.getUseSites().add(new UseSite(UseSite.Type.TYPE_PARAMETER_OR_BOUND, C));
        E.getUseSites().add(new UseSite(UseSite.Type.IS_THROWN, Cm));

        //and one more thing - we declare E as coming from supplementary archives, so that it is automagically
        //removed once it is not used anymore.
        A.setInApi(true);
        B.setInApi(true);
        C.setInApi(true);
        E.setInApi(true);
        E.setInApiThroughUse(true);

        //setup the type map
        typeMap.put(A.getDeclaringElement(), A);
        typeMap.put(B.getDeclaringElement(), B);
        typeMap.put(C.getDeclaringElement(), C);
        typeMap.put(E.getDeclaringElement(), E);
        environment.setTypeMap(typeMap);
    }

    @Test
    public void testRootTypesAffected() throws Exception {
        createEnvironment();
        forest.getRoots().remove(B);
        Assert.assertFalse(A.getUseSites().contains(new UseSite(UseSite.Type.ANNOTATES, B)));
    }

    @Test
    public void testRootTypesRemoved() throws Exception {
        createEnvironment();
        Cm.getParent().getChildren().remove(Cm);
        Assert.assertFalse(forest.getRoots().contains(E));    }

    @Test
    public void testInnerTypesAffected() {

    }

    @Test
    public void testInnerTypesRemoved() {

    }
}
