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
package org.revapi.java.checks.classes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;

import org.revapi.CoIterator;
import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.TypeEnvironment;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class InheritanceChainChanged extends CheckBase {

    private static final TypeVisitor<Boolean, Void> IS_MISSING_VISITOR =
            new SimpleTypeVisitor8<Boolean, Void>(false) {
                @Override
                public Boolean visitError(ErrorType t, Void __) {
                    return true;
                }

                @Override
                public Boolean visitDeclared(DeclaredType t, Void aVoid) {
                    return t.asElement().asType().getKind() == TypeKind.ERROR;
                }
            };

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.CLASS);
    }

    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaTypeElement> types = popIfActive();
        if (types != null) {

            List<Difference> ret = new ArrayList<>();

            @SuppressWarnings("unchecked")
            List<TypeMirror> oldSuperClasses = (List<TypeMirror>) types.context[0];
            @SuppressWarnings("unchecked")
            List<TypeMirror> newSuperClasses = (List<TypeMirror>) types.context[1];

            reportMissingSuperTypes(ret, oldSuperClasses, Code.MISSING_OLD_SUPERTYPE, types);
            reportMissingSuperTypes(ret, newSuperClasses, Code.MISSING_NEW_SUPERTYPE, types);
            if (!ret.isEmpty()) {
                return ret;
            }

            Comparator<TypeMirror> typeNameComparator = Comparator.comparing(Util::toUniqueString);

            List<TypeMirror> removedSuperClasses = new ArrayList<>();
            List<TypeMirror> addedSuperClasses = new ArrayList<>();

            oldSuperClasses.sort(typeNameComparator);
            newSuperClasses.sort(typeNameComparator);

            CoIterator<TypeMirror> iterator = new CoIterator<>(oldSuperClasses.iterator(), newSuperClasses.iterator(),
                    typeNameComparator);
            while (iterator.hasNext()) {
                iterator.next();

                TypeMirror oldType = iterator.getLeft();
                TypeMirror newType = iterator.getRight();

                if (oldType == null) {
                    addedSuperClasses.add(newType);
                } else if (newType == null) {
                    removedSuperClasses.add(oldType);
                }
            }

            //this will give us the equivalent of removed/added superclasses but ordered by the inheritance chain
            //not by name
            removedSuperClasses = retainInCopy(oldSuperClasses, removedSuperClasses);
            addedSuperClasses = retainInCopy(newSuperClasses, addedSuperClasses);

            Iterator<TypeMirror> removedIt = removedSuperClasses.iterator();
            Iterator<TypeMirror> addedIt = addedSuperClasses.iterator();

            //always report the most concrete classes
            if (removedIt.hasNext()) {
                removedIt.next();
            }

            if (addedIt.hasNext()) {
                addedIt.next();
            }

            //ok, now we only have super types left of the most concrete removed/added super class.
            //we are only going to report those that changed their inheritance hierarchy in the other version of the API.
            removeClassesWithEquivalentSuperClassChain(removedIt, getOldTypeEnvironment(), getNewTypeEnvironment());
            removeClassesWithEquivalentSuperClassChain(addedIt, getNewTypeEnvironment(), getOldTypeEnvironment());

            for (TypeMirror t : removedSuperClasses) {
                String str = Util.toHumanReadableString(t);
                ret.add(createDifference(Code.CLASS_NO_LONGER_INHERITS_FROM_CLASS,
                        Code.attachmentsFor(types.oldElement, types.newElement, "superClass", str)));
            }

            for (TypeMirror t : addedSuperClasses) {
                String str = Util.toHumanReadableString(t);
                Code code = types.oldElement.getDeclaringElement().getModifiers().contains(Modifier.FINAL)
                        ? Code.CLASS_FINAL_CLASS_INHERITS_FROM_NEW_CLASS
                        : Code.CLASS_NON_FINAL_CLASS_INHERITS_FROM_NEW_CLASS;

                ret.add(createDifference(code, Code.attachmentsFor(types.oldElement, types.newElement, "superClass", str)));

                //additionally add a difference about checked exceptions
                if (changedToCheckedException(getNewTypeEnvironment().getTypeUtils(), t, oldSuperClasses)) {
                    ret.add(createDifference(Code.CLASS_NOW_CHECKED_EXCEPTION,
                            Code.attachmentsFor(types.oldElement, types.newElement)));
                }
            }

            return ret;
        }

        return null;
    }

    @Override
    protected void doVisitClass(JavaTypeElement oldEl, JavaTypeElement newEl) {
        if (!isBothAccessible(oldEl, newEl)) {
            return;
        }

        TypeElement oldType = oldEl.getDeclaringElement();
        TypeElement newType = newEl.getDeclaringElement();

        List<TypeMirror> oldSuperTypes = Util
                .getAllSuperClasses(getOldTypeEnvironment().getTypeUtils(), oldType.asType());
        List<TypeMirror> newSuperTypes = Util
                .getAllSuperClasses(getNewTypeEnvironment().getTypeUtils(), newType.asType());

        if (oldSuperTypes.size() != newSuperTypes.size()) {
            pushActive(oldEl, newEl, oldSuperTypes, newSuperTypes);
        } else {
            Types oldTypes = getOldTypeEnvironment().getTypeUtils();
            Types newTypes = getNewTypeEnvironment().getTypeUtils();

            for (int i = 0; i < oldSuperTypes.size(); ++i) {
                //need to erase them all so that we get only true type changes. formal type parameter changes
                //are captured by SuperTypeParametersChanged check
                TypeMirror oldSuperClass = oldTypes.erasure(oldSuperTypes.get(i));
                TypeMirror newSuperClass = newTypes.erasure(newSuperTypes.get(i));

                if (!Util.isSameType(oldSuperClass, newSuperClass)) {
                    pushActive(oldEl, newEl, oldSuperTypes, newSuperTypes);
                    break;
                }
            }
        }
    }

    private boolean changedToCheckedException(@Nonnull Types newTypeEnv, @Nonnull TypeMirror newType,
            @Nonnull List<TypeMirror> oldTypes) {

        if ("java.lang.Exception".equals(Util.toHumanReadableString(newType))) {
            return isTypeThrowable(oldTypes);
        } else {
            for (TypeMirror sc : Util.getAllSuperClasses(newTypeEnv, newType)) {
                if ("java.lang.Exception".equals(Util.toHumanReadableString(sc))) {
                    return isTypeThrowable(oldTypes);
                }
            }
        }

        return false;
    }

    private boolean isTypeThrowable(@Nonnull List<TypeMirror> superClassesOfType) {
        for (TypeMirror sc : superClassesOfType) {
            if (Util.toHumanReadableString(sc).equals("java.lang.Throwable")) {
                return true;
            }
        }

        return false;
    }

    private List<String> superClassChainAsUniqueStrings(@Nonnull TypeMirror cls, @Nonnull Types env) {
        List<TypeMirror> supers = Util.getAllSuperClasses(env, cls);
        List<String> ret = new ArrayList<>(supers.size());

        for (TypeMirror s : supers) {
            ret.add(Util.toUniqueString(env.erasure(s)));
        }

        return ret;
    }

    private void removeClassesWithEquivalentSuperClassChain(Iterator<TypeMirror> candidates,
            TypeEnvironment candidateEnvironment, TypeEnvironment oppositeEnvironment) {

        while (candidates.hasNext()) {
            boolean report = true;

            TypeMirror candidate = candidates.next();
            String typeName = Util.toHumanReadableString(candidate);
            TypeElement el = oppositeEnvironment.getElementUtils().getTypeElement(typeName);
            if (el != null) {
                TypeMirror opposite = el.asType();

                List<String> candidateSuperChain = superClassChainAsUniqueStrings(candidate,
                        candidateEnvironment.getTypeUtils());

                List<String> oppositeSuperChain = superClassChainAsUniqueStrings(opposite,
                        oppositeEnvironment.getTypeUtils());

                report = !candidateSuperChain.equals(oppositeSuperChain);
            }

            if (!report) {
                candidates.remove();
            }
        }
    }

    private List<TypeMirror> retainInCopy(List<TypeMirror> all, List<TypeMirror> retained) {
        List<TypeMirror> tmp = new ArrayList<>(all);
        tmp.retainAll(retained);
        return tmp;
    }

    private void reportMissingSuperTypes(List<Difference> diffs, List<TypeMirror> superTypes, Code code,
            ActiveElements<?> activeElements) {
        for(TypeMirror t : superTypes) {
            if (isMissing(t)) {
                String type = Util.toHumanReadableString(t);
                diffs.add(createDifferenceWithExplicitParams(code, Code.attachmentsFor(
                        activeElements.oldElement, activeElements.newElement,
                        "superClass", type), type));
            }
        }
    }

    private static boolean isMissing(TypeMirror type) {
        return type.accept(IS_MISSING_VISITOR, null);
    }
}
