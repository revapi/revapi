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
package org.revapi.java.model;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor8;

import org.revapi.Archive;
import org.revapi.java.compilation.ClassPathUseSite;
import org.revapi.java.compilation.InheritedUseSite;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.compilation.UseSitePath;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.UseSite;
import org.revapi.java.spi.Util;
import org.revapi.query.Filter;

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
    private boolean inApiThroughUse;
    private Map<UseSite.Type, Map<TypeElement, Set<JavaModelElement>>> usedTypes;
    private Map<UseSite.Type, Map<TypeElement, Set<UseSitePath>>> rawUsedTypes;

    /**
     * This is a helper constructor used only in {@link MissingClassElement}. Inheritors using this constructor need
     * to make sure that they also override any and all methods that require a non-null element.
     *
     * @param env           probing environment
     * @param binaryName    the binary name of the class
     * @param canonicalName the canonical name of the class
     */
    TypeElement(ProbingEnvironment env, Archive archive, String binaryName, String canonicalName) {
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
    public TypeElement(ProbingEnvironment env, Archive archive, javax.lang.model.element.TypeElement element,
            DeclaredType type) {
        super(env, archive, element, type);
        binaryName = env.getElementUtils().getBinaryName(element).toString();
        canonicalName = element.getQualifiedName().toString();
    }

    @Nonnull
    @Override
    protected String getHumanReadableElementType() {
        switch (element.getKind()) {
            case ANNOTATION_TYPE:
                return "@interface";
            case CLASS:
                return "class";
            case ENUM:
                return "enum";
            case INTERFACE:
                return "interface";
            default:
                return "class";
        }
    }

    public String getBinaryName() {
        return binaryName;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    @Override public boolean isInAPI() {
        return inApi;
    }

    @Override public boolean isInApiThroughUse() {
        return inApiThroughUse;
    }

    @Override public Set<UseSite> getUseSites() {
        if (useSites == null) {
            if (rawUseSites == null) {
                useSites = new HashSet<>(1);
            } else {
                useSites = rawUseSites.stream()
                        .map(u -> {
                            if (u instanceof InheritedUseSite) {
                                JavaModelElement model = getModel(u.site, ((InheritedUseSite) u).inheritor,
                                        u.indexInParent);
                                return model == null ? null : new UseSite(u.useType, model);
                            } else {
                                JavaModelElement model = getModel(u.site, null, u.indexInParent);
                                return model == null ? null : new UseSite(u.useType, model);
                            }
                        }).filter(Objects::nonNull)
                        .collect(toSet());
            }
            rawUseSites = null;
        }

        return useSites;
    }

    /**
     * This provides the types used by this type. The keys are the types of use, values are maps from the used type
     * to the set of concrete users of the type (the users represent some child of this element).
     *
     * @return the types used by this type
     */
    public Map<UseSite.Type, Map<TypeElement, Set<JavaModelElement>>> getUsedTypes() {
        if (usedTypes == null) {
            usedTypes = new HashMap<>();
            if (rawUsedTypes != null) {
                for (Map.Entry<UseSite.Type, Map<TypeElement, Set<UseSitePath>>> e : rawUsedTypes.entrySet()) {
                    Map<TypeElement, Set<JavaModelElement>> value = e.getValue().entrySet().stream()
                            .collect(toMap(
                                    Map.Entry::getKey,
                                    entry -> entry.getValue().stream().map(path -> {
                                        int index = -1;
                                        if (path instanceof VariableElement && path.useSite instanceof ExecutableElement) {
                                            //find the index of the method parameter
                                            index = path.useSite.getEnclosingElement().getEnclosedElements()
                                                    .indexOf(path.useSite);
                                        }

                                        TypeElement owner = null;

                                        if (path.owner != null) {
                                            owner = (TypeElement) getModel(path.owner, null, -1);
                                        }

                                        return getModel(path.useSite, owner, index);
                                    }).filter(Objects::nonNull).collect(toSet())));

                    usedTypes.put(e.getKey(), value);
                }
            }
        }
        return usedTypes;
    }

    public void setRawUsedTypes(Map<UseSite.Type, Map<TypeElement, Set<UseSitePath>>> usedTypes) {
        this.rawUsedTypes = usedTypes;
    }

    public void setInApi(boolean inApi) {
        this.inApi = inApi;
    }

    public void setInApiThroughUse(boolean inApiThroughUse) {
        this.inApiThroughUse = inApiThroughUse;
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

    @Override
    public TypeElement clone() {
        return (TypeElement) super.clone();
    }

    private JavaModelElement getModel(Element element, TypeElement owner, int indexInParent) {
        return element.accept(new SimpleElementVisitor8<JavaModelElement, Void>() {
            @Override public JavaModelElement visitVariable(VariableElement e, Void ignored) {
                if (e.getEnclosingElement() instanceof javax.lang.model.element.TypeElement) {
                    //this is a field
                    TypeElement type = findModelType(e.getEnclosingElement());
                    if (type == null) {
                        return null;
                    }

                    List<FieldElement> fs = type.searchChildren(FieldElement.class, false,
                            Filter.shallow(f -> f.getDeclaringElement().equals(e)));
                    return fs.isEmpty() ? null : fs.get(0);
                } else if (e.getEnclosingElement() instanceof javax.lang.model.element.ExecutableElement) {
                    //this is a method parameter
                    Element methodEl = e.getEnclosingElement();
                    TypeElement type = findModelType(methodEl.getEnclosingElement());
                    if (type == null) {
                        return null;
                    }

                    List<MethodElement> ms = type.searchChildren(MethodElement.class, false,
                            Filter.shallow(m -> m.getDeclaringElement().equals(methodEl)));

                    if (ms.isEmpty()) {
                        return null;
                    }

                    MethodElement method = ms.get(0);

                    //now look for the parameter
                    List<MethodParameterElement> params =
                            method.searchChildren(MethodParameterElement.class, false, Filter.shallow(p -> true));

                    return params.size() > indexInParent ? params.get(indexInParent) : null;
                } else {
                    return null;
                }
            }

            @Override public JavaModelElement visitType(javax.lang.model.element.TypeElement e, Void ignored) {
                return findModelType(e);
            }

            @Override public JavaModelElement visitExecutable(ExecutableElement e, Void ignored) {
                TypeElement type = findModelType(e.getEnclosingElement());
                if (type == null) {
                    return null;
                }

                List<MethodElement> ms = type.searchChildren(MethodElement.class, false,
                        Filter.shallow(m -> m.getDeclaringElement().equals(e)));

                return ms.isEmpty() ? null : ms.get(0);
            }

            private TypeElement findModelType(Element enclosingElement) {
                if (owner == null) {
                    return environment.getTypeMap().get(enclosingElement);
                } else {
                    return owner;
                }
            }
        }, null);
    }
}
