/*
 * Copyright 2014 Lukas Krejci
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

package org.revapi.java.compilation;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor7;
import javax.lang.model.util.SimpleTypeVisitor7;
import javax.lang.model.util.Types;

import org.objectweb.asm.Type;

import org.revapi.API;
import org.revapi.java.model.FieldElement;
import org.revapi.java.model.JavaElementForest;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.MethodParameterElement;
import org.revapi.java.model.MissingClassElement;
import org.revapi.java.model.RawUseSite;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.TypeEnvironment;
import org.revapi.java.spi.UseSite;
import org.revapi.java.spi.Util;
import org.revapi.query.Filter;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class ProbingEnvironment implements TypeEnvironment {
    private final API api;
    private volatile ProcessingEnvironment processingEnvironment;
    private final CountDownLatch compilationProgressLatch = new CountDownLatch(1);
    private final CountDownLatch compilationEnvironmentTeardownLatch = new CountDownLatch(1);
    private final JavaElementForest tree;
    private final Map<String, Set<RawUseSite>> useSiteMap = new HashMap<>();

    public ProbingEnvironment(API api) {
        this.api = api;
        this.tree = new JavaElementForest(api);
    }

    public API getApi() {
        return api;
    }

    public CountDownLatch getCompilationTeardownLatch() {
        return compilationEnvironmentTeardownLatch;
    }

    public CountDownLatch getCompilationProgressLatch() {
        return compilationProgressLatch;
    }

    public JavaElementForest getTree() {
        return tree;
    }

    public void setProcessingEnvironment(ProcessingEnvironment env) {
        this.processingEnvironment = env;
    }

    @Nonnull
    @Override
    @SuppressWarnings("ConstantConditions")
    public Elements getElementUtils() {
        return processingEnvironment == null ? null :
            new MissingTypeAwareDelegatingElements(processingEnvironment.getElementUtils());
    }

    @Nonnull
    @Override
    @SuppressWarnings("ConstantConditions")
    public Types getTypeUtils() {
        return processingEnvironment == null ? null :
            new MissingTypeAwareDelegatingTypes(processingEnvironment.getTypeUtils());
    }

    @Override
    public boolean isExplicitPartOfAPI(@Nonnull TypeElement type) {
        if (CheckBase.isAccessible(type)) {
            // XXX protected inner class of a final class is I guess pretty much inaccessible, but it's currently
            // claimed accessible...
            return true;
        }

        final Elements elements = getElementUtils();

        String binaryName = elements.getBinaryName(type).toString();
        Set<RawUseSite> sites = useSiteMap.get(binaryName);
        if (sites == null || sites.isEmpty()) {
            return false;
        }

        Boolean res = visitRawUseSites(binaryName, sites, new RawUseSiteVisitor<Boolean, Void>() {
            @Override
            public Boolean visit(String binaryName, RawUseSite site, Void ignored) {
                TypeElement type = Util.findTypeByBinaryName(elements, site.getSiteClass());
                boolean accessible = CheckBase.isAccessible(type);

                return accessible ? true : null;
            }
        }, null);

        return res != null && res;
    }

    /**
     * Keys are binary names of classes
     */
    @Nonnull
    public Map<String, Set<RawUseSite>> getUseSiteMap() {
        return useSiteMap;
    }

    @Nonnull
    @Override
    public Set<UseSite> getUseSites(@Nonnull TypeElement type) {
        Set<RawUseSite> rawSites = getUseSiteMap().get(type.getQualifiedName().toString());
        if (rawSites == null || rawSites.isEmpty()) {
            return Collections.emptySet();
        }

        final Elements elements = getElementUtils();

        HashSet<UseSite> ret = new HashSet<>(rawSites.size());
        for (RawUseSite ru : rawSites) {

            UseSite u = toUseSite(type, ru, elements);

            ret.add(u);
        }

        return ret;
    }

    @Nullable
    @Override
    public <R, P> R visitUseSites(@Nonnull TypeElement type, @Nonnull final UseSite.Visitor<R, P> visitor,
        @Nullable final P parameter) {

        final Elements elements = getElementUtils();

        String binaryName = elements.getBinaryName(type).toString();

        Set<RawUseSite> sites = useSiteMap.get(binaryName);
        if (sites == null) {
            return null;
        }

        return visitRawUseSites(binaryName, sites, new RawUseSiteVisitor<R, P>() {
            @Override
            public R visit(String binaryName, RawUseSite site, P parameter) {
                TypeElement type = Util.findTypeByBinaryName(elements, binaryName);
                if (type == null) {
                    return null;
                }

                UseSite use = toUseSite(type, site, elements);

                return visitor.visit(type, use, parameter);
            }
        }, parameter);
    }

    private <R, P> R visitRawUseSites(String binaryName, Set<RawUseSite> sites, RawUseSiteVisitor<R, P> visitor,
        P parameter) {

        R ret = null;
        for (RawUseSite site : sites) {
            ret = visitor.visit(binaryName, site, parameter);
            if (ret != null) {
                return ret;
            }

            String siteClass = site.getSiteClass();
            Set<RawUseSite> nextSites = useSiteMap.get(siteClass);
            if (nextSites != null && !nextSites.isEmpty()) {
                ret = visitRawUseSites(siteClass, nextSites, visitor, parameter);
                if (ret != null) {
                    return ret;
                }
            }
        }

        return ret;
    }

    private UseSite toUseSite(TypeElement type, final RawUseSite ru, final Elements elements) {
        List<JavaTypeElement> userTypes = tree.search(JavaTypeElement.class, true, new Filter<JavaTypeElement>() {
            @Override
            public boolean applies(@Nullable JavaTypeElement element) {
                if (element instanceof org.revapi.java.model.TypeElement) {
                    return ((org.revapi.java.model.TypeElement) element).getBinaryName()
                        .equals(ru.getSiteClass());
                } else {
                    return element != null &&
                        elements.getBinaryName(element.getModelElement()).contentEquals(ru.getSiteClass());
                }
            }

            @Override
            public boolean shouldDescendInto(@Nullable Object element) {
                return element instanceof JavaTypeElement;
            }
        }, null);

        JavaTypeElement t;
        if (userTypes.isEmpty()) {
            t = new MissingClassElement(this, ru.getSiteClass(), ru.getSiteClass());
        } else {
            t = userTypes.get(0);
        }

        final JavaTypeElement userType = t;
        org.revapi.Element user = null;

        MethodElement method;

        switch (ru.getUseType()) {
        case ANNOTATES:
            switch (ru.getSiteType()) {
            case CLASS:
                user = userType;
                break;
            case FIELD:
                for (FieldElement f : userType.searchChildren(FieldElement.class, false, null)) {
                    if (f.getModelElement().getSimpleName().contentEquals(ru.getSiteName())) {
                        user = f;
                        break;
                    }
                }
                break;
            case METHOD:
                user = findMatchingMethod(ru, userType);
                break;
            case METHOD_PARAMETER:
                method = findMatchingMethod(ru, userType);
                if (method != null) {
                    List<MethodParameterElement> params = method
                        .searchChildren(MethodParameterElement.class, false, null);
                    if (params.size() > ru.getSitePosition()) {
                        user = params.get(ru.getSitePosition());
                    }
                }
                break;
            }
            break;
        case HAS_TYPE:
            for (FieldElement f : userType.searchChildren(FieldElement.class, false, null)) {
                if (f.getModelElement().getSimpleName().contentEquals(ru.getSiteName())) {
                    user = f;
                    break;
                }
            }
            break;
        case IS_IMPLEMENTED:
            if (hasMatchingType(elements, type, userType.getModelElement().getInterfaces())) {
                user = userType;
            }
            break;
        case IS_INHERITED:
            if (hasMatchingType(elements, type,
                Collections.singleton(userType.getModelElement().getSuperclass()))) {

                user = userType;
            }
            break;
        case IS_THROWN:
            method = findMatchingMethod(ru, userType);
            if (method != null) {
                if (hasMatchingType(elements, type, method.getModelElement().getThrownTypes())) {
                    user = method;
                }
            }

            break;
        case PARAMETER_TYPE:
            method = findMatchingMethod(ru, userType);
            if (method != null) {
                List<MethodParameterElement> params = method
                    .searchChildren(MethodParameterElement.class, false, null);

                if (params.size() > ru.getSitePosition()) {
                    MethodParameterElement parameter = params.get(ru.getSitePosition());
                    if (hasMatchingType(elements, type,
                        Collections.singleton(parameter.getModelElement().asType()))) {

                        user = method;
                    }
                }
            }
            break;
        case RETURN_TYPE:
            method = findMatchingMethod(ru, userType);
            if (method != null) {
                if (hasMatchingType(elements, type,
                    Collections.singleton(method.getModelElement().getReturnType()))) {

                    user = method;
                }
            }
            break;
        }

        if (user == null) {
            throw new IllegalStateException(
                "Could not find the corresponding model element for use: " + ru + " of type " +
                    type.getQualifiedName());
        }

        return new UseSite(ru.getUseType(), user);
    }

    private MethodElement findMatchingMethod(RawUseSite methodUseSite,
        JavaTypeElement containingType) {

        return findMatchingMethod(methodUseSite,
            containingType.searchChildren(MethodElement.class, false, null));
    }

    private MethodElement findMatchingMethod(RawUseSite methodUseSite, List<MethodElement> candidates) {
        Type[] parameterTypes = Type.getArgumentTypes(methodUseSite.getSiteDescriptor());
        Type returnType = Type.getReturnType(methodUseSite.getSiteDescriptor());


        for (MethodElement m : candidates) {
            ExecutableElement method = m.getModelElement();
            if (method.getSimpleName().contentEquals(methodUseSite.getSiteName())) {
                if (!equals(returnType, method.getReturnType())) {
                    continue;
                }

                List<? extends VariableElement> params = method.getParameters();
                if (params.size() != parameterTypes.length) {
                    continue;
                }

                int i = 0;
                for (VariableElement p : method.getParameters()) {
                    if (!equals(parameterTypes[i], p.asType())) {
                        break;
                    }
                    ++i;
                }

                if (i == parameterTypes.length) {
                    return m;
                }
            }
        }

        return null;
    }

    private static boolean hasMatchingType(final Elements elements, final TypeElement type,
        Iterable<? extends TypeMirror> types) {

        // for missing types, we use the binary name for comparison, otherwise we use the more precise fqn.
        final boolean isErrorType = type.asType().getKind() == TypeKind.ERROR;
        final Name comparableName = isErrorType ? elements.getBinaryName(type) : type.getQualifiedName();

        for (TypeMirror t : types) {
            boolean found = t.accept(new SimpleTypeVisitor7<Boolean, Void>(false) {

                SimpleElementVisitor7<Boolean, Void> typeNameChecker = new SimpleElementVisitor7<Boolean, Void>(false) {
                    @Override
                    public Boolean visitType(TypeElement e, Void ignored) {
                        if (isErrorType) {
                            return elements.getBinaryName(e).contentEquals(comparableName);
                        } else {
                            return comparableName.contentEquals(e.getQualifiedName());
                        }
                    }
                };

                @Override
                public Boolean visitError(ErrorType t, Void ignored) {
                    return t.asElement().accept(typeNameChecker, null);
                }

                @Override
                public Boolean visitArray(ArrayType t, Void ignored) {
                    return visit(t.getComponentType(), ignored);
                }

                @Override
                public Boolean visitDeclared(DeclaredType t, Void ignored) {
                    return t.asElement().accept(typeNameChecker, null);
                }

                @Override
                public Boolean visitTypeVariable(TypeVariable t, Void ignored) {
                    return visit(t.getUpperBound(), ignored);
                }
            }, null);

            if (found) {
                return true;
            }
        }

        return false;
    }

    private static Element findByName(String simpleName, Iterable<? extends Element> elements) {
        for (Element e : elements) {
            if (e.getSimpleName().contentEquals(simpleName)) {
                return e;
            }
        }

        return null;
    }

    private boolean equals(final Type type, TypeMirror mirror) {
        if (type == null || mirror == null) {
            return false;
        }

        switch (type.getSort()) {
        case Type.ARRAY:
            TypeMirror elementType = mirror.accept(new SimpleTypeVisitor7<TypeMirror, Void>(null) {
                @Override
                public TypeMirror visitArray(ArrayType t, Void ignored) {
                    return t.getComponentType();
                }

                @Override
                public TypeMirror visitTypeVariable(TypeVariable t, Void ignored) {
                    return visit(getTypeUtils().erasure(t), null);
                }
            }, null);

            return equals(type.getElementType(), elementType);
        case Type.OBJECT:
            return mirror.accept(new SimpleTypeVisitor7<Boolean, Void>(false) {
                SimpleElementVisitor7<Boolean, Void> binaryNameChecker = new SimpleElementVisitor7<Boolean, Void>(
                    false) {
                    @Override
                    public Boolean visitType(TypeElement e, Void ignored) {
                        return getElementUtils().getBinaryName(e).contentEquals(type.getClassName());
                    }
                };

                @Override
                public Boolean visitDeclared(DeclaredType t, Void ignored) {
                    return t.asElement().accept(binaryNameChecker, null);
                }

                @Override
                public Boolean visitError(ErrorType t, Void aVoid) {
                    return t.asElement().accept(binaryNameChecker, null);
                }

                @Override
                public Boolean visitTypeVariable(TypeVariable t, Void ignored) {
                    return visit(getTypeUtils().erasure(t), null);
                }

                @Override
                public Boolean visitWildcard(WildcardType t, Void ignored) {
                    return visit(getTypeUtils().capture(t), null);
                }
            }, null);
        case Type.METHOD:
            return false;
        case Type.VOID:
            return mirror.getKind() == TypeKind.VOID;
        case Type.BOOLEAN:
            return mirror.getKind() == TypeKind.BOOLEAN;
        case Type.BYTE:
            return mirror.getKind() == TypeKind.BYTE;
        case Type.CHAR:
            return mirror.getKind() == TypeKind.CHAR;
        case Type.DOUBLE:
            return mirror.getKind() == TypeKind.DOUBLE;
        case Type.FLOAT:
            return mirror.getKind() == TypeKind.FLOAT;
        case Type.INT:
            return mirror.getKind() == TypeKind.INT;
        case Type.LONG:
            return mirror.getKind() == TypeKind.LONG;
        case Type.SHORT:
            return mirror.getKind() == TypeKind.SHORT;
        }

        return false;
    }

    private interface RawUseSiteVisitor<R, P> {
        R visit(String binaryName, RawUseSite site, P parameter);
    }
}
