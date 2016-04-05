/*
 * Copyright 2016 Lukas Krejci
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
 *
 */
package org.revapi.java.filters;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.SimpleElementVisitor8;

import org.revapi.API;
import org.revapi.Archive;
import org.revapi.Element;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.TypeEnvironment;
import org.revapi.query.Filter;

/**
 * @author Lukas Krejci
 * @since 0.7.0
 */
public final class PackageFilter extends AbstractIncludeExcludeFilter {

    //the purpose of this map is to have a package represented by a SINGLE object
    private final Map<String, JavaPackageElement> packageElements = new HashMap<>();

    public PackageFilter() {
        super("revapi.java.filter.packages", "/META-INF/package-filter-schema.json");
    }

    @Override
    protected boolean canBeReIncluded(JavaModelElement element) {
//        //we can only re-evaluate the inclusions on top level classes. Nothing else can get re-included...
//        return element instanceof JavaTypeElement
//                && element.getModelElement().getEnclosingElement() instanceof PackageElement;
        return false;
    }

//    @Override
//    protected Predicate<String> composeTest(List<String> fullMatches, List<Pattern> patterns) {
//        if (fullMatches != null && fullMatches.size() > 0) {
//            return s -> fullMatches.stream().filter(s::startsWith).findAny().isPresent();
//        } else if (patterns != null && patterns.size() > 0) {
//            List<Pattern> modifiedPatterns = patterns.stream().map(p -> Pattern.compile(p.pattern() + ".*"))
//                    .collect(toList());
//            return s -> modifiedPatterns.stream().anyMatch(p -> p.matcher(s).matches());
//        } else {
//            return null;
//        }
//    }

    @Override
    protected Stream<String> getTestedElementRepresentations(JavaModelElement element) {
        return Stream.of(getPackageOf(element.getModelElement()).getQualifiedName().toString());
    }

    @Override
    protected void validateConfiguration(boolean excludes, List<String> fullMatches, List<Pattern> patterns,
            boolean regexes) {
        if (!regexes) {
            ClassFilter.validateFullMatches(excludes, fullMatches);
        }
    }

    @Override
    public boolean applies(@Nullable Element element) {
        if (doNothing || !(element instanceof JavaModelElement)) {
            return true;
        }

        //the Revapi java extension currently doesn't represent the packages in the model but for this to work correctly
        //we need to pretend that they are.
        //Also for the filter to work correctly, we need to pretend the packages are hierarchical, which the
        //javax.lang.model.PackageElement doesn't support.
        PackageElement pkg = getPackageOf(((JavaModelElement) element).getModelElement());
        if (pkg == null) {
            return true;
        }

        String packageName = pkg.getQualifiedName().toString();
        JavaPackageElement pkgE;

        synchronized (packageElements) {
            pkgE = packageElements.get(packageName);
            if (pkgE == null) {
                pkgE = new JavaPackageElement(pkg);
                packageElements.put(packageName, pkgE);
            }
        }

        return super.applies(pkgE);
    }

    private PackageElement getPackageOf(javax.lang.model.element.Element element) {
        return element.accept(new SimpleElementVisitor8<PackageElement, Void>() {
            @Override
            public PackageElement visitVariable(VariableElement e, Void ignored) {
                return e.getEnclosingElement().accept(this, null);
            }

            @Override
            public PackageElement visitExecutable(ExecutableElement e, Void ignored) {
                return e.getEnclosingElement().accept(this, null);
            }

            @Override
            public PackageElement visitType(TypeElement e, Void ignored) {
                return e.getEnclosingElement().accept(this, null);
            }

            @Override
            public PackageElement visitPackage(PackageElement e, Void ignored) {
                return e;
            }

            @Override
            public PackageElement visitTypeParameter(TypeParameterElement e, Void aVoid) {
                return e.getEnclosingElement().accept(this, null);
            }
        }, null);
    }

    private static final class JavaPackageElement implements JavaModelElement {

        private final String name;
        private final PackageElement pkg;

        public JavaPackageElement(PackageElement pkg) {
            this.name = "package " + pkg.getQualifiedName().toString();
            this.pkg = pkg;
        }

        @Nonnull
        @Override
        public PackageElement getModelElement() {
            return pkg;
        }

        @Nonnull
        @Override
        public TypeEnvironment getTypeEnvironment() {
            return null;
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

        @Nullable
        @Override
        public Element getParent() {
            return null;
        }

        @Override
        public void setParent(@Nullable Element parent) {
        }

        @Nonnull
        @Override
        public SortedSet<? extends Element> getChildren() {
            return Collections.emptyNavigableSet();
        }

        @Nonnull
        @Override
        public String getFullHumanReadableString() {
            return name;
        }

        @Nonnull
        @Override
        public <T extends Element> List<T> searchChildren(@Nonnull Class<T> resultType, boolean recurse,
                @Nullable Filter<? super T> filter) {
            return Collections.emptyList();
        }

        @Override
        public <T extends Element> void searchChildren(@Nonnull List<T> results, @Nonnull Class<T> resultType,
                boolean recurse, @Nullable Filter<? super T> filter) {
        }

        @Nonnull
        @Override
        public <T extends Element> Iterator<T> iterateOverChildren(@Nonnull Class<T> resultType, boolean recurse,
                @Nullable Filter<? super T> filter) {
            return Collections.emptyIterator();
        }

        @Override
        public int compareTo(Element o) {
            return 0;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
