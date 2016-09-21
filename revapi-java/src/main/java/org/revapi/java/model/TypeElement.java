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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.lang.model.element.*;
import javax.lang.model.element.PackageElement;
import javax.lang.model.util.SimpleElementVisitor8;

import org.revapi.Archive;
import org.revapi.java.compilation.ClassPathUseSite;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.UseSite;
import org.revapi.java.spi.Util;
import org.revapi.query.Filter;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class TypeElement extends JavaElementBase<javax.lang.model.element.TypeElement> implements JavaTypeElement {
    private static final List<Modifier> ACCESSIBLE_MODIFIERS = Arrays.asList(Modifier.PUBLIC, Modifier.PROTECTED);

    private final String binaryName;
    private final String canonicalName;
    private Set<UseSite> useSites;
    private Set<ClassPathUseSite> rawUseSites;
    private Set<TypeElement> subClasses;
    private boolean membersAccessible;
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
        super(env, archive, null);
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
    public TypeElement(ProbingEnvironment env, Archive archive, javax.lang.model.element.TypeElement element) {
        super(env, archive, element);
        binaryName = env.getElementUtils().getBinaryName(element).toString();
        canonicalName = element.getQualifiedName().toString();
    }

    public boolean isInnerClass() {
        int dotPos = -1;

        do {
            dotPos = canonicalName.indexOf('.', dotPos + 1);
            if (dotPos >= 0 && binaryName.charAt(dotPos) == '$') {
                return true;
            }
        } while (dotPos >= 0);

        return false;
    }

    @Nonnull
    @Override
    protected String getHumanReadableElementType() {
        return "class";
    }

    @Nonnull
    @Override
    @SuppressWarnings("ConstantConditions")
    public javax.lang.model.element.TypeElement getModelElement() {
        //even though environment.getElementUtils() is marked @Nonnull, we do the check here, because
        //it actually IS null for a while during initialization of the forest during compilation.
        //we do this so that toString() works even under those conditions.
        if (element == null && environment.hasProcessingEnvironment()) {
            element = environment.getElementUtils().getTypeElement(canonicalName);
        }
        return element;
    }

    public String getBinaryName() {
        return binaryName;
    }

    public String getCanonicalName() {
        return canonicalName;
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

    @Override public boolean isMembersAccessible() {
        if (subClasses != null) {
            if (isAccessibleByModifier(getModelElement())) {
                membersAccessible = allEnclosersAccessibleByModifier(getModelElement());
            } else {
                membersAccessible = subClasses.stream()
                        .filter(TypeElement::isMembersAccessible)
                        .findAny().isPresent();
            }

            subClasses = null;
        }

        return membersAccessible;
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

    public void setSubClasses(Set<TypeElement> subClasses) {
        this.subClasses = subClasses;
    }

    @Override
    public int compareTo(@Nonnull org.revapi.Element o) {
        if (!(o.getClass().equals(TypeElement.class))) {
            return JavaElementFactory.compareByType(this, o);
        }

        return binaryName.compareTo(((TypeElement) o).binaryName);
    }

    @Nonnull
    @Override
    @SuppressWarnings("ConstantConditions")
    public String getFullHumanReadableString() {
        javax.lang.model.element.TypeElement el = getModelElement();
        //see getModelElement() for why we do the null check here even if getModelElement() is @Nonnull
        return getHumanReadableElementType() + " " + (el == null ? canonicalName : Util.toHumanReadableString(el));
    }

    @Override
    protected String createComparableSignature() {
        //this isn't used, because compareTo is implemented differently
        return null;
    }

    private boolean isAccessibleByModifier(Element type) {
        return !Collections.disjoint(type.getModifiers(), ACCESSIBLE_MODIFIERS);
    }

    private boolean allEnclosersAccessibleByModifier(javax.lang.model.element.TypeElement type) {
        Element el = type;
        while (el != null && !(el instanceof PackageElement)) {
            if (!isAccessibleByModifier(el)) {
                return false;
            }

            el = el.getEnclosingElement();
        }

        return true;
    }

    private JavaModelElement getModel(Element element, int indexInParent) {
        return element.accept(new SimpleElementVisitor8<JavaModelElement, Void>() {
            @Override public JavaModelElement visitVariable(VariableElement e, Void ignored) {
                if (e.getEnclosingElement() instanceof javax.lang.model.element.TypeElement) {
                    //this is a field
                    TypeElement type = environment.getTypeMap().get(e.getEnclosingElement());
                    Name fieldName = e.getSimpleName();
                    List<FieldElement> fs = type.searchChildren(FieldElement.class, false,
                            Filter.flat(f -> fieldName.contentEquals(f.getModelElement().getSimpleName())));
                    return fs.get(0);
                } else if (e.getEnclosingElement() instanceof javax.lang.model.element.ExecutableElement) {
                    //this is a method parameter
                    TypeElement type = environment.getTypeMap().get(e.getEnclosingElement().getEnclosingElement());
                    String methodSig = Util.toUniqueString(e.getEnclosingElement().asType());
                    List<MethodElement> ms = type.searchChildren(MethodElement.class, false,
                            Filter.flat(m -> Util.toUniqueString(m.getModelElement().asType()).equals(methodSig)));

                    MethodElement method = ms.get(0);

                    //now look for the parameter
                    List<MethodParameterElement> params =
                            method.searchChildren(MethodParameterElement.class, false, Filter.flat(p -> true));

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
                        Filter.flat(f -> Util.toUniqueString(f.getModelElement().asType()).equals(methodSig)));

                return fs.get(0);
            }
        }, null);
    }
}
