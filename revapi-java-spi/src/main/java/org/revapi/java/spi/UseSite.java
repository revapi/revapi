/*
 * Copyright 2014-2023 Lukas Krejci
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
package org.revapi.java.spi;

import java.util.Set;

import javax.lang.model.element.Modifier;

import org.revapi.Reference;

/**
 * @author Lukas Krejci
 *
 * @since 0.1
 */
public final class UseSite extends Reference<JavaElement> {

    /**
     * The way the used class is used by the use site.
     */
    public enum Type implements Reference.Type<JavaElement> {
        /**
         * The used class annotates the use site.
         */
        ANNOTATES,

        /**
         * The used class is inherited by the use site (class).
         */
        IS_INHERITED,

        /**
         * The used class is implemented by the use site (class).
         */
        IS_IMPLEMENTED,

        /**
         * The use site (field) has the type of the used class.
         */
        HAS_TYPE,

        /**
         * The use site (method) returns instances of the used class.
         */
        RETURN_TYPE,

        /**
         * One of the parameters of the use site (method) has the type of the used class.
         */
        PARAMETER_TYPE,

        /**
         * The use site (method) throws exceptions of the type of the used class.
         */
        IS_THROWN,

        /**
         * The used class contains the use site (inner class).
         */
        CONTAINS,

        /**
         * The used class is used as a type parameter or a bound of a type variable or wildcard on the use site (which
         * can be a class, field, method or a method parameter).
         */
        TYPE_PARAMETER_OR_BOUND;

        /**
         * Consider using {@link UseSite#isMovingToApi()}, if possible.
         *
         * @return true if this type of use makes the used type part of the API even if it wasn't originally part of it.
         */
        public boolean isMovingToApi() {
            switch (this) {
            case ANNOTATES:
            case CONTAINS:
            case IS_INHERITED:
            case IS_IMPLEMENTED:
                return false;
            default:
                return true;
            }
        }

        @Override
        public String getName() {
            return name();
        }
    }

    public UseSite(Type useType, JavaElement site) {
        super(site, useType);
    }

    @Override
    public Type getType() {
        return (Type) super.getType();
    }

    /**
     * This checks if the {@link #getType() type} of the use causes the use site to be part of the API but it also
     * checks that the site actually can cause the used type to be in the API. In particular, protected members of final
     * classes cannot move to the API, because no outside caller can access those members.
     * <p>
     * Implementation note: This can only be reliably used once the element forest is completely constructed. The first
     * point when this is safe is during the element forest pruning. In particular, one cannot use this in
     * {@link org.revapi.TreeFilter}s because those are used during element forest construction.
     *
     * @return {@code true} if the use site moves the used type to the API, {@code false} otherwise.
     */
    public boolean isMovingToApi() {
        if (!getType().isMovingToApi()) {
            return false;
        }

        return isEffectivelyInApi(getElement());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("UseSite[");
        sb.append("site=").append(getElement());
        sb.append(", useType=").append(getType());
        sb.append(']');
        return sb.toString();
    }

    private static boolean isEffectivelyInApi(JavaElement el) {
        if (!(el instanceof JavaModelElement)) {
            return true;
        }

        JavaModelElement me = (JavaModelElement) el;
        JavaModelElement parent = me.getParent();

        if (me instanceof JavaMethodParameterElement) {
            return isEffectivelyInApi(parent);
        }

        Set<Modifier> modifiers = me.getDeclaringElement().getModifiers();

        if (modifiers.contains(Modifier.PUBLIC)) {
            return parent == null || isEffectivelyInApi(parent);
        }

        if (modifiers.contains(Modifier.PROTECTED)) {
            // a protected element in a final class is effectively not part of the api, because it cannot be accessed
            // outside it. If we found a usage of it, it must be in a context which has access to it.
            if (parent != null && parent.getDeclaringElement().getModifiers().contains(Modifier.FINAL)) {
                return false;
            }
            return parent == null || isEffectivelyInApi(parent);
        } else {
            // package-private or private
            return false;
        }
    }
}
