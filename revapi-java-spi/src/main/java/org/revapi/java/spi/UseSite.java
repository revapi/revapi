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
package org.revapi.java.spi;

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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("UseSite[");
        sb.append("site=").append(getElement());
        sb.append(", useType=").append(getType());
        sb.append(']');
        return sb.toString();
    }
}
