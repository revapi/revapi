/*
 * Copyright 2014-2017 Lukas Krejci
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
package org.revapi.java.compilation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.util.ElementFilter;

import org.junit.Rule;
import org.junit.Test;
import org.revapi.API;
import org.revapi.Archive;
import org.revapi.java.AbstractJavaElementAnalyzerTest;
import org.revapi.java.JavaArchiveAnalyzer;
import org.revapi.java.model.JavaElementForest;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.UseSite;
import org.revapi.java.test.support.Jar;
import org.revapi.simple.FileArchive;

public class ForestPruningTest extends AbstractJavaElementAnalyzerTest {

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
    private MethodElement Em;
    private TypeElement F;
    private MethodElement Fctor;
    private TypeElement FFF;
    private MethodElement FFFctor;

    private ProbingEnvironment environment;
    private JavaArchiveAnalyzer analyzer;

    private void createEnvironment() throws Exception {
        API fakeApi = API.builder().build();
        Archive archive = new FileArchive(null);

        environment = new ProbingEnvironment(fakeApi);
        HashMap<javax.lang.model.element.TypeElement, TypeElement> typeMap = new HashMap<>();

        Jar.Environment env = jar.from().classPathSources("/usesites/", "A.java", "B.java", "C.java", "E.java", "F.java").build().analyze();
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
        method = ElementFilter.methodsIn(type.getEnclosedElements()).get(0);
        methodModel = new MethodElement(environment, archive, method, (ExecutableType) method.asType());
        Em = methodModel;
        E.getChildren().add(Em);

        type = env.elements().getTypeElement("F");
        model = new TypeElement(environment, archive, type, (DeclaredType) type.asType());
        F = model;
        forest.getRoots().add(model);
        method = ElementFilter.constructorsIn(type.getEnclosedElements()).get(0);
        methodModel = new MethodElement(environment, archive, method, (ExecutableType) method.asType());
        model.getChildren().add(methodModel);

        type = env.elements().getTypeElement("F.FF");
        model = new TypeElement(environment, archive, type, (DeclaredType) type.asType());
        FFF = model;
        F.getChildren().add(model);
        method = ElementFilter.constructorsIn(type.getEnclosedElements()).get(0);
        methodModel = new MethodElement(environment, archive, method, (ExecutableType) method.asType());
        FFFctor = methodModel;
        FFF.getChildren().add(FFFctor);

        //k now we have all the elements in our model, but we still need to add the use sites
        A.getUseSites().add(new UseSite(UseSite.Type.ANNOTATES, B));
        B.getUseSites().add(new UseSite(UseSite.Type.IS_IMPLEMENTED, C));
        B.getUseSites().add(new UseSite(UseSite.Type.PARAMETER_TYPE, Cm));
        B.getUseSites().add(new UseSite(UseSite.Type.TYPE_PARAMETER_OR_BOUND, C));
        E.getUseSites().add(new UseSite(UseSite.Type.IS_THROWN, Cm));
        FFF.getUseSites().add(new UseSite(UseSite.Type.RETURN_TYPE, Em));
        F.getUseSites().add(new UseSite(UseSite.Type.CONTAINS, FFF));

        B.getUsedTypes().computeIfAbsent(UseSite.Type.ANNOTATES, __ -> new HashMap<>())
                .computeIfAbsent(A, __ -> new HashSet<>()).add(B);

        C.getUsedTypes().computeIfAbsent(UseSite.Type.IS_IMPLEMENTED, __ -> new HashMap<>())
                .computeIfAbsent(B, __ -> new HashSet<>()).add(C);
        C.getUsedTypes().computeIfAbsent(UseSite.Type.PARAMETER_TYPE, __ -> new HashMap<>())
                .computeIfAbsent(B, __ -> new HashSet<>()).add(Cm);
        C.getUsedTypes().computeIfAbsent(UseSite.Type.TYPE_PARAMETER_OR_BOUND, __ -> new HashMap<>())
                .computeIfAbsent(B, __ -> new HashSet<>()).add(C);
        C.getUsedTypes().computeIfAbsent(UseSite.Type.IS_THROWN, __ -> new HashMap<>())
                .computeIfAbsent(E, __ -> new HashSet<>()).add(Cm);

        E.getUsedTypes().computeIfAbsent(UseSite.Type.RETURN_TYPE, __ -> new HashMap<>())
                .computeIfAbsent(FFF, __ -> new HashSet<>()).add(Em);

        FFF.getUsedTypes().computeIfAbsent(UseSite.Type.CONTAINS, __ -> new HashMap<>())
                .computeIfAbsent(F, __ -> new HashSet<>()).add(FFF);

        //and one more thing - we declare E as coming from supplementary archives, so that it is automagically
        //removed once it is not used anymore.
        A.setInApi(true);
        B.setInApi(true);
        C.setInApi(true);
        E.setInApi(false);
        E.setInApiThroughUse(false);
        FFF.setInApi(true);
        FFF.setInApiThroughUse(true);

        //setup the type map
        typeMap.put(A.getDeclaringElement(), A);
        typeMap.put(B.getDeclaringElement(), B);
        typeMap.put(C.getDeclaringElement(), C);
        typeMap.put(E.getDeclaringElement(), E);
        typeMap.put(F.getDeclaringElement(), F);
        typeMap.put(FFF.getDeclaringElement(), FFF);

        environment.setTypeMap(typeMap);

        analyzer = new JavaArchiveAnalyzer(fakeApi, null, null, true);
    }

    @Test
    public void testRootTypesAffected() throws Exception {
        createEnvironment();

        forest.getRoots().remove(C);
        analyzer.prune(forest);

        assertFalse(B.getUseSites().contains(new UseSite(UseSite.Type.IS_IMPLEMENTED, C)));
        assertFalse(B.getUseSites().contains(new UseSite(UseSite.Type.PARAMETER_TYPE, Cm)));
        assertFalse(B.getUseSites().contains(new UseSite(UseSite.Type.TYPE_PARAMETER_OR_BOUND, C)));
        assertFalse(E.getUseSites().contains(new UseSite(UseSite.Type.IS_THROWN, Cm)));

        forest.getRoots().remove(B);
        analyzer.prune(forest);

        assertFalse(A.getUseSites().contains(new UseSite(UseSite.Type.ANNOTATES, B)));
    }

    @Test
    public void testRootTypesRemoved() throws Exception {
        createEnvironment();
        Cm.getParent().getChildren().remove(Cm);
        analyzer.prune(forest);
        assertFalse(forest.getRoots().contains(E));
    }

    @Test
    public void testInnerTypesAffected() throws Exception {
        createEnvironment();

        assertTrue(forest.getRoots().contains(F));
        forest.getRoots().remove(E);
        analyzer.prune(forest);
        assertFalse(forest.getRoots().contains(F));
    }
}
