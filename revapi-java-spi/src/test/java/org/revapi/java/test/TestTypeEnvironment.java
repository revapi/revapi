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

package org.revapi.java.test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.revapi.API;
import org.revapi.Archive;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.TypeEnvironment;
import org.revapi.java.spi.UseSite;
import org.revapi.simple.SimpleElement;

/**
* @author Lukas Krejci
* @since 0.2
*/
public class TestTypeEnvironment implements TypeEnvironment {

    private final Elements elements;
    private final Types types;
    private final Map<TypeElement, Set<UseSite>> useSites = new HashMap<>();

    public static Builder builder(Jar.Environment env) {
        return new Builder(env.getElementUtils(), env.getTypeUtils());
    }

    private TestTypeEnvironment(Elements elements, Types types) {
        this.elements = elements;
        this.types = types;
    }

    @Nonnull
    @Override
    public Elements getElementUtils() {
        return elements;
    }

    @Nonnull
    @Override
    public Types getTypeUtils() {
        return types;
    }

    @Nullable
    @Override
    public <R, P> R visitUseSites(@Nonnull TypeElement type, @Nonnull UseSite.Visitor<R, P> visitor,
        @Nullable P parameter) {

        Set<UseSite> sites = useSites.get(type);
        if (sites != null && !sites.isEmpty()) {
            for (UseSite s : sites) {
                R ret = visitor.visit(type, s, parameter);
                if (ret != null) {
                    return ret;
                }
            }
        }

        return visitor.end(type, parameter);
    }

    @Nonnull
    @Override
    public Set<TypeElement> getAccessibleSubclasses(@Nonnull TypeElement type) {
        return Collections.emptySet();
    }

    @Override
    public boolean isExplicitlyIncluded(Element element) {
        return true;
    }

    @Override
    public boolean isExplicitlyExcluded(Element element) {
        return false;
    }

    public static class Builder {
        private Elements elements;
        private Types types;
        private Map<TypeElement, Set<RawUseSite>> useSites = new HashMap<>();

        private Builder(Elements elements, Types types) {
            this.elements = elements;
            this.types = types;
        }

        public Builder addUseSite(Element user, UseSite.Type useType, TypeElement type) {
            Set<RawUseSite> uses = useSites.get(type);
            if (uses == null) {
                uses = new HashSet<>();
                useSites.put(type, uses);
            }

            uses.add(new RawUseSite(useType, user));

            return this;
        }

        public TestTypeEnvironment build() {
            if (elements == null) {
                throw new IllegalStateException("elements not set");
            }

            if (types == null) {
                throw new IllegalStateException("types not set");
            }

            TestTypeEnvironment ret = new TestTypeEnvironment(elements, types);
            for(Map.Entry<TypeElement, Set<RawUseSite>> e : useSites.entrySet()) {
                TypeElement type = e.getKey();
                Set<RawUseSite> rawUses = e.getValue();

                Set<UseSite> uses = convert(rawUses, ret);

                ret.useSites.put(type, uses);
            }

            return ret;
        }

        private Set<UseSite> convert(Set<RawUseSite> uses, TestTypeEnvironment env) {
            HashSet<UseSite> ret = new HashSet<>();
            for(RawUseSite r : uses) {
                ret.add(convert(r, env));
            }

            return ret;
        }

        private UseSite convert(RawUseSite use, TestTypeEnvironment env) {
            switch (use.user.getKind()) {
            case CLASS: case CONSTRUCTOR: case ENUM: case FIELD: case INTERFACE: case METHOD: case PARAMETER:
                return new UseSite(use.type, new DummyModelElement(use.user, env));
            default:
                throw new IllegalArgumentException("unsupported element kind: " + use.user.getKind());
            }
        }

        private static class RawUseSite {
            private final UseSite.Type type;
            private final Element user;

            private RawUseSite(UseSite.Type type, Element user) {
                this.type = type;
                this.user = user;
            }
        }

        private static class DummyModelElement extends SimpleElement implements JavaModelElement {
            private final Element element;
            private final TestTypeEnvironment environment;

            public DummyModelElement(Element element, TestTypeEnvironment environment) {
                this.element = element;
                this.environment = environment;
            }

            @Nonnull
            @Override
            public Element getModelElement() {
                return element;
            }

            @Nonnull
            @Override
            public TypeEnvironment getTypeEnvironment() {
                return environment;
            }

            @Nonnull
            @Override
            public API getApi() {
                return null;
            }

            @Nullable
            @Override
            public Archive getArchive() {
                return null;
            }

            @Override
            public int compareTo(org.revapi.Element o) {
                return toString().compareTo(o.toString());
            }

            public String toString() {
                StringBuilder bld = new StringBuilder();

                Element e = element;

                while (e != null) {
                    bld.append(e.getSimpleName()).append("->");
                    e = e.getEnclosingElement();
                }

                return bld.toString();
            }
        }
    }
}
