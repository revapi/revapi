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

package org.revapi.java.model;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor8;

import org.revapi.Archive;
import org.revapi.java.FlatFilter;
import org.revapi.java.compilation.ClassPathUseSite;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.UseSite;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class TypeElement extends JavaElementBase<javax.lang.model.element.TypeElement, DeclaredType> implements JavaTypeElement {
    private final String binaryName;
    private final String canonicalName;
    private Set<UseSite> useSites;
    private Set<ClassPathUseSite> rawUseSites;
    private boolean inApi;

    /**
     * This is a helper constructor used only during probing the class files. This is to ensure that we have a
     * "bare bones" type element available even before we have functioning compilation infrastructure in
     * the environment.
     *
     * @param env           probing environment
     * @param binaryName    the binary name of the class
     * @param canonicalName the canonical name of the class
     */
    public TypeElement(ProbingEnvironment env, Archive archive, String binaryName, String canonicalName) {
        super(env, archive, null, null);
        this.binaryName = binaryName;
        this.canonicalName = canonicalName;
    }

    /**
     * This constructor is used under "normal working conditions" when the probing environment already has
     * the compilation infrastructure available (which is assumed since otherwise it would not be possible to obtain
     * instances of the javax.lang.model.element.TypeElement interface).
     *
     * @param env     the probing environment
     * @param element the model element to be represented
     */
    public TypeElement(ProbingEnvironment env, Archive archive, javax.lang.model.element.TypeElement element, DeclaredType type) {
        super(env, archive, element, type);
        binaryName = env.getElementUtils().getBinaryName(element).toString();
        canonicalName = element.getQualifiedName().toString();
    }

    @Nonnull
    @Override
    protected String getHumanReadableElementType() {
        return "class";
    }

    public String getBinaryName() {
        return binaryName;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    @Override protected List<TypeMirror> getSuperTypesForInheritance() {
        return Util.getAllSuperTypes(getTypeEnvironment().getTypeUtils(), getModelRepresentation());
    }

    @Override public Set<UseSite> getUseSites() {
        if (useSites == null) {
            if (rawUseSites == null) {
                useSites = Collections.emptySet();
            } else {
                useSites = rawUseSites.stream()
                        .map(u -> new UseSite(u.useType, getModel(u.site, u.indexInParent)))
                        .collect(Collectors.toSet());
            }
            rawUseSites = null;
        }

        return useSites;
    }

    @Override public boolean isInAPI() {
        return inApi;
    }

    public void setInApi(boolean inApi) {
        this.inApi = inApi;
    }

    public void setRawUseSites(Set<ClassPathUseSite> rawUseSites) {
        this.rawUseSites = rawUseSites;
    }

    @Override
    public int compareTo(@Nonnull org.revapi.Element o) {
        if (!(o.getClass().equals(TypeElement.class))) {
            return JavaElementFactory.compareByType(this, o);
        }

        return binaryName.compareTo(((TypeElement) o).binaryName);
    }

    @Override protected String createFullHumanReadableString() {
        TypeMirror rep = getModelRepresentation();
        return getHumanReadableElementType() + " " + (rep == null ? canonicalName : Util.toHumanReadableString(rep));
    }

    @Override
    protected String createComparableSignature() {
        //this isn't used, because compareTo is implemented differently
        return null;
    }

    private JavaModelElement getModel(Element element, int indexInParent) {
        return element.accept(new SimpleElementVisitor8<JavaModelElement, Void>() {
            @Override public JavaModelElement visitVariable(VariableElement e, Void ignored) {
                if (e.getEnclosingElement() instanceof javax.lang.model.element.TypeElement) {
                    //this is a field
                    TypeElement type = environment.getTypeMap().get(e.getEnclosingElement());
                    Name fieldName = e.getSimpleName();
                    List<FieldElement> fs = type.searchChildren(FieldElement.class, false,
                            FlatFilter.by(f -> fieldName.contentEquals(f.getDeclaringElement().getSimpleName())));
                    return fs.get(0);
                } else if (e.getEnclosingElement() instanceof javax.lang.model.element.ExecutableElement) {
                    //this is a method parameter
                    TypeElement type = environment.getTypeMap().get(e.getEnclosingElement().getEnclosingElement());
                    String methodSig = Util.toUniqueString(e.getEnclosingElement().asType());
                    List<MethodElement> ms = type.searchChildren(MethodElement.class, false,
                            FlatFilter.by(m -> Util.toUniqueString(m.getDeclaringElement().asType()).equals(methodSig)));

                    MethodElement method = ms.get(0);

                    //now look for the parameter
                    List<MethodParameterElement> params =
                            method.searchChildren(MethodParameterElement.class, false, FlatFilter.by(p -> true));

                    return params.get(indexInParent);
                } else {
                    return null;
                }
            }

            @Override public JavaModelElement visitType(javax.lang.model.element.TypeElement e, Void ignored) {
                return environment.getTypeMap().get(e);
            }

            @Override public JavaModelElement visitExecutable(ExecutableElement e, Void ignored) {
                TypeElement type = environment.getTypeMap().get(e.getEnclosingElement());
                String methodSig = Util.toUniqueString(e.asType());
                List<MethodElement> fs = type.searchChildren(MethodElement.class, false,
                        FlatFilter.by(f -> Util.toUniqueString(f.getDeclaringElement().asType()).equals(methodSig)));

                return fs.get(0);
            }
        }, null);
    }
}
