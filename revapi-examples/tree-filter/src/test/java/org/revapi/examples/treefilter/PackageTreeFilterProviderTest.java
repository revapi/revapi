/*
 * Copyright 2014-2021 Lukas Krejci
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
package org.revapi.examples.treefilter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.revapi.AnalysisContext;
import org.revapi.ApiAnalyzer;
import org.revapi.ArchiveAnalyzer;
import org.revapi.FilterStartResult;
import org.revapi.Ternary;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaTypeElement;

class PackageTreeFilterProviderTest {

    PackageTreeFilterProvider filter = new PackageTreeFilterProvider();

    @Test
    @SuppressWarnings("unchecked")
    void testAcceptsElementsFromPackage() throws Exception {
        filter.initialize(AnalysisContext.builder().build().copyWithConfiguration(new ObjectMapper().readTree("\"com.acme\"")));

        ArchiveAnalyzer<JavaElement> archiveAnalyzer = mock(ArchiveAnalyzer.class);
        ApiAnalyzer<JavaElement> apiAnalyzer = mock(ApiAnalyzer.class);

        when(archiveAnalyzer.getApiAnalyzer()).thenReturn(apiAnalyzer);
        when(apiAnalyzer.getExtensionId()).thenReturn("revapi.java");

        JavaTypeElement element = mock(JavaTypeElement.class);
        TypeElement typeInPackage = mock(TypeElement.class);
        PackageElement pkg = mock(PackageElement.class);
        Name pkgName = mock(Name.class);

        when(element.getDeclaringElement()).thenReturn(typeInPackage);
        when(typeInPackage.getEnclosingElement()).thenReturn(pkg);
        when(pkg.getQualifiedName()).thenReturn(pkgName);
        when(pkgName.toString()).thenReturn("com.acme");

        FilterStartResult res = filter.filterFor(archiveAnalyzer).get().start(element);
        assertSame(Ternary.TRUE, res.getMatch());
        assertFalse(res.isInherited());
        assertTrue(res.getDescend().toBoolean(false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDoesntAcceptElementsFromOtherPackage() throws Exception {
        filter.initialize(AnalysisContext.builder().build().copyWithConfiguration(new ObjectMapper().readTree("\"com.acme\"")));

        ArchiveAnalyzer<JavaElement> archiveAnalyzer = mock(ArchiveAnalyzer.class);
        ApiAnalyzer<JavaElement> apiAnalyzer = mock(ApiAnalyzer.class);

        when(archiveAnalyzer.getApiAnalyzer()).thenReturn(apiAnalyzer);
        when(apiAnalyzer.getExtensionId()).thenReturn("revapi.java");

        JavaTypeElement element = mock(JavaTypeElement.class);
        TypeElement typeInPackage = mock(TypeElement.class);
        PackageElement pkg = mock(PackageElement.class);
        Name pkgName = mock(Name.class);

        when(element.getDeclaringElement()).thenReturn(typeInPackage);
        when(typeInPackage.getEnclosingElement()).thenReturn(pkg);
        when(pkg.getQualifiedName()).thenReturn(pkgName);
        when(pkgName.toString()).thenReturn("corp.acme");

        FilterStartResult res = filter.filterFor(archiveAnalyzer).get().start(element);
        assertSame(Ternary.FALSE, res.getMatch());
        assertFalse(res.isInherited());
        assertFalse(res.getDescend().toBoolean(true));
    }
}
