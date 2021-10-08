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
package org.revapi.java.compilation;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import static org.revapi.java.AnalysisConfiguration.MissingClassReporting.ERROR;
import static org.revapi.java.AnalysisConfiguration.MissingClassReporting.IGNORE;
import static org.revapi.java.AnalysisConfiguration.MissingClassReporting.REPORT;
import static org.revapi.java.model.JavaElementFactory.elementFor;
import static org.revapi.java.spi.UseSite.Type.IS_THROWN;
import static org.revapi.java.spi.UseSite.Type.PARAMETER_TYPE;
import static org.revapi.java.spi.UseSite.Type.RETURN_TYPE;
import static org.revapi.java.spi.UseSite.Type.TYPE_PARAMETER_OR_BOUND;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor8;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.revapi.Archive;
import org.revapi.FilterFinishResult;
import org.revapi.FilterStartResult;
import org.revapi.Ternary;
import org.revapi.TreeFilter;
import org.revapi.java.AnalysisConfiguration;
import org.revapi.java.model.AbstractJavaElement;
import org.revapi.java.model.AnnotationElement;
import org.revapi.java.model.FieldElement;
import org.revapi.java.model.InitializationOptimizations;
import org.revapi.java.model.JavaElementBase;
import org.revapi.java.model.JavaElementFactory;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.MethodParameterElement;
import org.revapi.java.model.MissingClassElement;
import org.revapi.java.spi.IgnoreCompletionFailures;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.UseSite;
import org.revapi.java.spi.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukas Krejci
 * 
 * @since 0.11.0
 */
final class ClasspathScanner {
    private static final Logger LOG = LoggerFactory.getLogger(ClasspathScanner.class);

    private static final List<Modifier> ACCESSIBLE_MODIFIERS = Arrays.asList(Modifier.PUBLIC, Modifier.PROTECTED);
    private static final String SYSTEM_CLASSPATH_NAME = "<system classpath>";

    private static final JavaFileManager.Location[] POSSIBLE_SYSTEM_CLASS_LOCATIONS = { StandardLocation.CLASS_PATH,
            StandardLocation.PLATFORM_CLASS_PATH };

    private static final ElementVisitor<Boolean, Void> IS_TYPE_PARAMETERIZED = new SimpleElementVisitor8<Boolean, Void>(
            false) {
        final TypeVisitor<Boolean, Void> isGeneric = new SimpleTypeVisitor8<Boolean, Void>(false) {
            @Override
            public Boolean visitArray(ArrayType t, Void __) {
                return visit(t.getComponentType());
            }

            @Override
            public Boolean visitDeclared(DeclaredType t, Void __) {
                boolean ret = t.getTypeArguments().stream().reduce(false, (prev, curr) -> prev || visit(curr),
                        Boolean::logicalOr);

                if (!ret) {
                    // we have to check the enclosing types of non-static inner classes, too
                    TypeMirror enclosing = t.getEnclosingType();

                    Element el = enclosing instanceof DeclaredType ? ((DeclaredType) t.getEnclosingType()).asElement()
                            : null;
                    if (el != null && !el.getModifiers().contains(Modifier.STATIC)) {
                        ret = visit(t.getEnclosingType());
                    }
                }

                return ret;
            }

            @Override
            public Boolean visitError(ErrorType t, Void __) {
                return visitDeclared(t, null);
            }

            @Override
            public Boolean visitExecutable(ExecutableType t, Void __) {
                return visit(t.getReturnType())
                        || t.getParameterTypes().stream().reduce(false, (prev, x) -> prev || visit(x),
                                Boolean::logicalOr)
                        || t.getThrownTypes().stream().reduce(false, (prev, x) -> prev || visit(x), Boolean::logicalOr);
            }

            @Override
            public Boolean visitTypeVariable(TypeVariable t, Void __) {
                return true;
            }

            @Override
            public Boolean visitWildcard(WildcardType t, Void __) {
                // don't consider <?> generic, because it always resolves to java.lang.Object
                if (t.getExtendsBound() != null) {
                    return visit(t.getExtendsBound());
                } else if (t.getSuperBound() != null) {
                    return visit(t.getSuperBound());
                } else {
                    return false;
                }
            }
        };

        @Override
        public Boolean visitVariable(VariableElement e, Void aVoid) {
            return e.asType().accept(isGeneric, null);
        }

        @Override
        public Boolean visitType(TypeElement e, Void aVoid) {
            return !e.getTypeParameters().isEmpty();
        }

        @Override
        public Boolean visitExecutable(ExecutableElement e, Void aVoid) {
            return isGeneric.visit(e.asType());
        }
    };

    private final StandardJavaFileManager fileManager;
    private final ProbingEnvironment environment;
    private final Map<Archive, File> classPath;
    private final Map<Archive, File> additionalClassPath;
    private final AnalysisConfiguration.MissingClassReporting missingClassReporting;
    private final boolean ignoreMissingAnnotations;
    private final TreeFilter<JavaElement> filter;
    private final TypeElement objectType;

    ClasspathScanner(StandardJavaFileManager fileManager, ProbingEnvironment environment, Map<Archive, File> classPath,
            Map<Archive, File> additionalClassPath, AnalysisConfiguration.MissingClassReporting missingClassReporting,
            boolean ignoreMissingAnnotations, TreeFilter<JavaElement> filter) {
        this.fileManager = fileManager;
        this.environment = environment;
        this.classPath = classPath;
        this.additionalClassPath = additionalClassPath;
        this.missingClassReporting = missingClassReporting == null ? REPORT : missingClassReporting;
        this.ignoreMissingAnnotations = ignoreMissingAnnotations;
        this.filter = filter;
        this.objectType = environment.getElementUtils().getTypeElement("java.lang.Object");
    }

    void initTree() throws IOException {
        List<ArchiveLocation> classPathLocations = classPath.keySet().stream().map(ArchiveLocation::new)
                .collect(toList());

        Scanner scanner = new Scanner();

        for (ArchiveLocation loc : classPathLocations) {
            scanner.scan(loc, classPath.get(loc.getArchive()), true);
        }

        SyntheticLocation allLoc = new SyntheticLocation();
        fileManager.setLocation(allLoc, classPath.values());

        Function<String, JavaFileObject> searchHard = className -> Stream
                .concat(Stream.of(allLoc), Stream.of(POSSIBLE_SYSTEM_CLASS_LOCATIONS)).map(l -> {
                    try {
                        return fileManager.getJavaFileForInput(l, className, JavaFileObject.Kind.CLASS);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }).filter(Objects::nonNull).findFirst().orElse(null);

        Set<TypeElement> lastUnknowns = Collections.emptySet();

        Map<String, ArchiveLocation> cachedArchives = new HashMap<>(additionalClassPath.size());

        while (!scanner.requiredTypes.isEmpty() && !lastUnknowns.equals(scanner.requiredTypes.keySet())) {
            lastUnknowns = new HashSet<>(scanner.requiredTypes.keySet());
            for (TypeElement t : lastUnknowns) {
                String name = environment.getElementUtils().getBinaryName(t).toString();
                JavaFileObject jfo = searchHard.apply(name);
                if (jfo == null) {
                    // this type is really missing
                    continue;
                }

                URI uri = jfo.toUri();
                String path;
                if ("jar".equals(uri.getScheme())) {
                    // we pass our archives as jars, so let's dig only into those
                    path = uri.getSchemeSpecificPart();

                    // jar:file:/path .. let's get rid of the "file:" part
                    int colonIdx = path.indexOf(':');
                    if (colonIdx >= 0) {
                        path = path.substring(colonIdx + 1);
                    }

                    // separate the file path from the in-jar path
                    path = path.substring(0, path.lastIndexOf('!'));

                    // remove superfluous forward slashes at the start of the path, if any
                    int lastSlashIdx = -1;
                    for (int i = 0; i < path.length() - 1; ++i) {
                        if (path.charAt(i) == '/' && path.charAt(i + 1) != '/') {
                            lastSlashIdx = i;
                            break;
                        }
                    }
                    if (lastSlashIdx > 0) {
                        path = path.substring(lastSlashIdx);
                    }
                } else {
                    path = uri.getPath();
                }

                ArchiveLocation loc = cachedArchives.get(path);
                if (loc == null) {
                    Archive ar = null;
                    for (Map.Entry<Archive, File> e : additionalClassPath.entrySet()) {
                        if (e.getValue().getAbsolutePath().equals(path)) {
                            ar = e.getKey();
                            break;
                        }
                    }

                    if (ar != null) {
                        loc = new ArchiveLocation(ar);
                        cachedArchives.put(path, loc);
                    }
                }

                if (loc != null) {
                    scanner.scanClass(loc, t, false);
                }
            }
        }

        // ok, so scanning the archives doesn't give us any new resolved classes that we need in the API...
        // let's scan the system classpath. What will be left after this will be the truly missing classes.

        lastUnknowns = Collections.emptySet();
        while (!scanner.requiredTypes.isEmpty() && !lastUnknowns.equals(scanner.requiredTypes.keySet())) {
            lastUnknowns = new HashSet<>(scanner.requiredTypes.keySet());

            ArchiveLocation systemClassPath = new ArchiveLocation(new Archive() {
                @Nonnull
                @Override
                public String getName() {
                    return SYSTEM_CLASSPATH_NAME;
                }

                @Nonnull
                @Override
                public InputStream openStream() throws IOException {
                    throw new UnsupportedOperationException();
                }
            });

            for (TypeElement t : lastUnknowns) {
                scanner.scanClass(systemClassPath, t, false);
            }
        }

        scanner.initEnvironment();
    }

    private final class Scanner {
        final Set<TypeElement> processed = new HashSet<>();
        final Map<TypeElement, Boolean> requiredTypes = new IdentityHashMap<>();
        final Map<TypeElement, TypeRecord> types = new IdentityHashMap<>();
        final TypeVisitor<TypeElement, Void> getTypeElement = new SimpleTypeVisitor8<TypeElement, Void>() {
            @Override
            protected TypeElement defaultAction(TypeMirror e, Void ignored) {
                throw new IllegalStateException("Could not determine the element of a type: " + e);
            }

            @Override
            public TypeElement visitDeclared(DeclaredType t, Void ignored) {
                return (TypeElement) t.asElement();
            }

            @Override
            public TypeElement visitTypeVariable(TypeVariable t, Void ignored) {
                return t.getUpperBound().accept(this, null);
            }

            @Override
            public TypeElement visitArray(ArrayType t, Void ignored) {
                return t.getComponentType().accept(this, null);
            }

            @Override
            public TypeElement visitPrimitive(PrimitiveType t, Void ignored) {
                return null;
            }

            @Override
            public TypeElement visitIntersection(IntersectionType t, Void aVoid) {
                return t.getBounds().get(0).accept(this, null);
            }

            @Override
            public TypeElement visitWildcard(WildcardType t, Void aVoid) {
                if (t.getExtendsBound() != null) {
                    return t.getExtendsBound().accept(this, null);
                } else if (t.getSuperBound() != null) {
                    return t.getSuperBound().accept(this, null);
                } else {
                    return environment.getElementUtils().getTypeElement("java.lang.Object");
                }
            }

            @Override
            public TypeElement visitNoType(NoType t, Void aVoid) {
                return null;
            }

            @Override
            public TypeElement visitError(ErrorType t, Void aVoid) {
                return (TypeElement) t.asElement();
            }
        };

        void scan(ArchiveLocation location, File path, boolean primaryApi) throws IOException {
            fileManager.setLocation(location, Collections.singleton(path));

            Iterable<? extends JavaFileObject> jfos = fileManager.list(location, "",
                    EnumSet.of(JavaFileObject.Kind.CLASS), true);

            for (JavaFileObject jfo : jfos) {
                TypeElement type = Util.findTypeByBinaryName(environment.getElementUtils(),
                        fileManager.inferBinaryName(location, jfo));

                // type can be null if it represents an anonymous or member class...
                if (type != null) {
                    scanClass(location, type, primaryApi);
                }
            }
        }

        void scanClass(ArchiveLocation loc, TypeElement type, boolean primaryApi) {
            try {
                if (processed.contains(type)) {
                    return;
                }

                // Technically, we could find this out later on in the method, but doing this here ensures that the
                // javac tries to fully load the class (and therefore throws any completion failures).
                // Doing this now ensures that we get a correct info about the class (like the enclosing element,
                // type kind, etc).
                TypeElement superType = getTypeElement.visit(IgnoreCompletionFailures.in(type::getSuperclass));

                // we need to be strict about hierarchy to adhere to the TreeFilter contract
                if (type.getEnclosingElement() instanceof TypeElement) {
                    TypeElement enclosing = (TypeElement) type.getEnclosingElement();
                    scanClass(loc, enclosing, primaryApi);
                    TypeRecord enclosingTr = getTypeRecord(enclosing);
                    if (!enclosingTr.inclusionState.getDescend().toBoolean(true)) {
                        // the enclosing type didn't want to descend into its children, but we approached it from
                        // the inner class (due to the order we encounter their file objects). We need to honor
                        // the decision of the enclosing class to not scan its children.
                        return;
                    }

                    // important to have this ALSO here, because scanning the enclosing class will scan
                    // also all the enclosed classes, thus this should bail out quickly (and also don't cause duplicate
                    // records for the same element)
                    if (processed.contains(type)) {
                        return;
                    }
                }

                processed.add(type);
                Boolean wasAnno = requiredTypes.remove(type);

                // type.asType() possibly not completely correct when dealing with inner class of a parameterized class
                boolean isError = type.asType().getKind() == TypeKind.ERROR;

                org.revapi.java.model.TypeElement t = isError ? new MissingClassElement(environment, type)
                        : new org.revapi.java.model.TypeElement(environment, loc.getArchive(), type,
                                (DeclaredType) type.asType());

                TypeRecord tr = getTypeRecord(type);

                if (type.getEnclosingElement() instanceof TypeElement) {
                    tr.parent = getTypeRecord((TypeElement) type.getEnclosingElement());
                    tr.parent.modelElement.getChildren().add(t);
                }

                tr.inclusionState = filter.start(t);

                tr.modelElement = t;
                // this will be revisited... in here we're just establishing the types that are in the API for sure...
                tr.inApi = primaryApi && !shouldBeIgnored(type) && (tr.parent == null || tr.parent.inApi);
                tr.primaryApi = primaryApi;

                if (isError) {
                    // we initialized the type record for the missing type but cannot continue further. Let's just
                    // re-add the type to the list of required types so that it can be looked for harder later on...
                    requiredTypes.put(type, wasAnno != null && wasAnno);
                    finishFiltering(tr, t);
                    return;
                }

                // make sure we always have java.lang.Object in the set of super types. If the current class' super type
                // is missing, we might not be able to climb the full hierarchy up to java.lang.Object. But that would
                // be highly misleading to the users.
                // Consider Object a superType of interfaces, too, so that we have the Object methods on them.
                if (!type.equals(objectType)) {
                    tr.superTypes.add(getTypeRecord(objectType));
                    if (!processed.contains(objectType)) {
                        requiredTypes.put(objectType, false);
                    }
                }

                if (superType != null) {
                    addUse(tr, type, superType, UseSite.Type.IS_INHERITED);
                    tr.superTypes.add(getTypeRecord(superType));
                    if (!processed.contains(superType)) {
                        requiredTypes.put(superType, false);
                    }
                }

                IgnoreCompletionFailures.in(type::getInterfaces).stream().map(getTypeElement::visit).forEach(e -> {
                    if (!processed.contains(e)) {
                        requiredTypes.put(e, false);
                    }
                    addUse(tr, type, e, UseSite.Type.IS_IMPLEMENTED);
                    tr.superTypes.add(getTypeRecord(e));
                });

                addTypeParamUses(tr, type, type.asType());

                if (tr.inclusionState.getDescend().toBoolean(true)) {
                    for (Element e : IgnoreCompletionFailures.in(type::getEnclosedElements)) {
                        switch (e.getKind()) {
                        case ANNOTATION_TYPE:
                        case CLASS:
                        case ENUM:
                        case INTERFACE:
                            addUse(tr, type, (TypeElement) e, UseSite.Type.CONTAINS);
                            scanClass(loc, (TypeElement) e, primaryApi);
                            break;
                        case CONSTRUCTOR:
                        case METHOD:
                            scanMethod(tr, (ExecutableElement) e);
                            break;
                        case ENUM_CONSTANT:
                        case FIELD:
                            scanField(tr, (VariableElement) e);
                            break;
                        }
                    }
                }

                type.getAnnotationMirrors().forEach(a -> scanAnnotation(tr, type, -1, a));

                finishFiltering(tr, t);
            } catch (Exception e) {
                LOG.error("Failed to scan class " + type.getQualifiedName().toString()
                        + ". Analysis results may be skewed.", e);
                TypeRecord tr = getTypeRecord(type);
                tr.errored = true;
                if (tr.modelElement != null && tr.inclusionState != null) {
                    finishFiltering(tr, tr.modelElement);
                } else {
                    tr.inclusionState = FilterStartResult.doesntMatch();
                }
            }
        }

        void placeInTree(TypeRecord typeRecord) {
            // we exploit the fact that constructTree processes the less-nested elements
            // first. So by this time, we can be sure that our parents have been processed
            if (typeRecord.parent == null) {
                // if it's top-level type, easy
                environment.getTree().getRootsUnsafe().add(typeRecord.modelElement);
            } else {
                if (typeRecord.parent.inTree) {
                    typeRecord.parent.modelElement.getChildren().add(typeRecord.modelElement);
                } else {
                    // if it's not top level type, but the parent is not in the tree, we found a "gap" in the model
                    // included.. Therefore we just add the this type to the first parent in the tree or to the top
                    // level if none found
                    TypeRecord parent = typeRecord.parent;
                    while (parent != null && (!parent.inTree || parent.modelElement == null)) {
                        parent = parent.parent;
                    }

                    if (parent == null) {
                        environment.getTree().getRootsUnsafe().add(typeRecord.modelElement);
                    } else {
                        parent.modelElement.getChildren().add(typeRecord.modelElement);
                    }
                }
            }
        }

        void scanField(TypeRecord owningType, VariableElement field) {
            if (shouldBeIgnored(field)) {
                owningType.inaccessibleDeclaredNonClassMembers.add(field);
                return;
            }

            owningType.accessibleDeclaredNonClassMembers.add(field);

            TypeElement fieldType = field.asType().accept(getTypeElement, null);

            // fieldType == null means primitive type, if no type can be found, exception is thrown
            if (fieldType != null) {
                addUse(owningType, field, fieldType, UseSite.Type.HAS_TYPE);
                addType(fieldType, false);
                addTypeParamUses(owningType, field, field.asType());
            }

            field.getAnnotationMirrors().forEach(a -> scanAnnotation(owningType, field, -1, a));
        }

        void scanMethod(TypeRecord owningType, ExecutableElement method) {
            if (shouldBeIgnored(method)) {
                owningType.inaccessibleDeclaredNonClassMembers.add(method);
                return;
            }

            owningType.accessibleDeclaredNonClassMembers.add(method);

            TypeElement returnType = method.getReturnType().accept(getTypeElement, null);
            if (returnType != null) {
                addUse(owningType, method, returnType, UseSite.Type.RETURN_TYPE);
                addType(returnType, false);
                addTypeParamUses(owningType, method, method.getReturnType());
            }

            int idx = 0;
            for (VariableElement p : method.getParameters()) {
                TypeElement pt = p.asType().accept(getTypeElement, null);
                if (pt != null) {
                    addUse(owningType, method, pt, UseSite.Type.PARAMETER_TYPE, idx);
                    addType(pt, false);
                    addTypeParamUses(owningType, method, p.asType());
                }

                for (AnnotationMirror a : p.getAnnotationMirrors()) {
                    scanAnnotation(owningType, p, idx, a);
                }
            }

            method.getThrownTypes().forEach(t -> {
                TypeElement ex = t.accept(getTypeElement, null);
                if (ex != null) {
                    addUse(owningType, method, ex, UseSite.Type.IS_THROWN);
                    addType(ex, false);
                    addTypeParamUses(owningType, method, t);
                }

                t.getAnnotationMirrors().forEach(a -> scanAnnotation(owningType, method, -1, a));
            });

            method.getAnnotationMirrors().forEach(a -> scanAnnotation(owningType, method, -1, a));
        }

        void scanAnnotation(TypeRecord owningType, Element annotated, int indexInParent, AnnotationMirror annotation) {
            TypeElement type = annotation.getAnnotationType().accept(getTypeElement, null);
            if (type != null) {
                addUse(owningType, annotated, type, UseSite.Type.ANNOTATES, indexInParent);
                addType(type, true);
            }
        }

        boolean addType(TypeElement type, boolean isAnnotation) {
            if (processed.contains(type)) {
                return true;
            }

            requiredTypes.put(type, isAnnotation);
            return false;
        }

        void addTypeParamUses(TypeRecord userType, Element user, TypeMirror usedType) {
            addTypeParamUses(userType, user, usedType, t -> addUse(userType, user, t, TYPE_PARAMETER_OR_BOUND));
        }

        void addTypeParamUses(TypeRecord userType, Element user, TypeMirror usedType, Consumer<TypeElement> addUseFn) {
            HashSet<String> visited = new HashSet<>(4);
            usedType.accept(new SimpleTypeVisitor8<Void, Void>() {
                @Override
                public Void visitIntersection(IntersectionType t, Void aVoid) {
                    t.getBounds().forEach(b -> b.accept(this, null));
                    return null;
                }

                @Override
                public Void visitArray(ArrayType t, Void ignored) {
                    return t.getComponentType().accept(this, null);
                }

                @Override
                public Void visitDeclared(DeclaredType t, Void ignored) {
                    String type = Util.toUniqueString(t);
                    if (!visited.contains(type)) {
                        visited.add(type);
                        if (t != usedType) {
                            TypeElement typeEl = (TypeElement) t.asElement();
                            addType(typeEl, false);
                            addUseFn.accept(typeEl);
                        }
                        t.getTypeArguments().forEach(a -> a.accept(this, null));
                    }
                    return null;
                }

                @Override
                public Void visitTypeVariable(TypeVariable t, Void ignored) {
                    return t.getUpperBound().accept(this, null);
                }

                @Override
                public Void visitWildcard(WildcardType t, Void ignored) {
                    TypeMirror bound = t.getExtendsBound();
                    if (bound != null) {
                        bound.accept(this, null);
                    }
                    bound = t.getSuperBound();
                    if (bound != null) {
                        bound.accept(this, null);
                    }
                    return null;
                }
            }, null);
        }

        void addUse(TypeRecord userType, Element user, TypeElement used, UseSite.Type useType) {
            addUse(userType, user, used, useType, -1);
        }

        void addUse(TypeRecord userType, Element user, TypeElement used, UseSite.Type useType, int indexInParent) {
            addUse(userType, user, used, useType, indexInParent, ClassPathUseSite::new);
        }

        void addUse(TypeRecord userType, Element user, TypeElement used, UseSite.Type useType, int indexInParent,
                UseSiteConstructor ctor) {
            TypeRecord usedTr = getTypeRecord(used);
            Set<ClassPathUseSite> sites = usedTr.useSites;
            sites.add(ctor.create(useType, user, indexInParent));

            Map<TypeRecord, Set<UseSitePath>> usedTypes = userType.usedTypes.computeIfAbsent(useType,
                    k -> new HashMap<>(4));

            usedTypes.computeIfAbsent(usedTr, __ -> new HashSet<>(4)).add(new UseSitePath(userType.javacElement, user));
        }

        TypeRecord getTypeRecord(TypeElement type) {
            TypeRecord rec = types.get(type);
            if (rec == null) {
                rec = new TypeRecord();
                rec.javacElement = type;
                int depth = 0;
                Element e = type.getEnclosingElement();
                while (e != null && e instanceof TypeElement) {
                    depth++;
                    e = e.getEnclosingElement();
                }
                rec.nestingDepth = depth;
                types.put(type, rec);
            }

            return rec;
        }

        void initEnvironment() {
            if (ignoreMissingAnnotations && !requiredTypes.isEmpty()) {
                removeAnnotatesUses();
            }

            moveInnerClassesOfPrimariesToApi();
            initChildren();
            determineApiStatus();

            if (missingClassReporting == IGNORE) {
                types.entrySet().removeIf(e -> e.getValue().modelElement == null
                        || e.getValue().modelElement instanceof MissingClassElement);
                requiredTypes.clear();
            } else if (missingClassReporting == REPORT && !requiredTypes.isEmpty()) {
                handleMissingClasses(types);
            }

            Set<TypeRecord> types = constructTree();

            if (missingClassReporting == ERROR) {
                List<String> reallyMissing = types.stream().filter(tr -> tr.modelElement instanceof MissingClassElement)
                        .map(tr -> tr.modelElement.getCanonicalName()).collect(toList());
                if (!reallyMissing.isEmpty()) {
                    throw new IllegalStateException("The following classes that contribute to the public API of "
                            + environment.getApi() + " could not be located: " + reallyMissing);
                }
            }

            environment.setTypeMap(types.stream().collect(toMap(tr -> tr.javacElement, tr -> tr.modelElement)));
        }

        private void handleMissingClasses(Map<TypeElement, TypeRecord> types) {
            Elements els = environment.getElementUtils();

            for (TypeElement t : requiredTypes.keySet()) {
                TypeElement type = els.getTypeElement(t.getQualifiedName());
                if (type == null) {
                    TypeRecord tr = this.types.get(t);
                    MissingClassElement mce = new MissingClassElement(environment, t);
                    if (tr == null) {
                        tr = new TypeRecord();
                    }
                    mce.setInApi(tr.inApi);
                    mce.setInApiThroughUse(tr.inApiThroughUse);
                    mce.setRawUseSites(tr.useSites);
                    tr.javacElement = mce.getDeclaringElement();
                    tr.modelElement = mce;
                    types.put(tr.javacElement, tr);
                }
            }
        }

        private Set<TypeRecord> constructTree() {
            Set<TypeRecord> types = new HashSet<>();
            Comparator<Map.Entry<TypeElement, TypeRecord>> byNestingDepth = (a, b) -> {
                TypeRecord ar = a.getValue();
                TypeRecord br = b.getValue();
                int ret = ar.nestingDepth - br.nestingDepth;
                if (ret == 0) {
                    ret = a.getKey().getQualifiedName().toString().compareTo(b.getKey().getQualifiedName().toString());
                }

                // the less nested classes need to come first
                return ret;
            };

            this.types.entrySet().stream().sorted(byNestingDepth).forEach(e -> {
                TypeElement t = e.getKey();
                TypeRecord r = e.getValue();

                if (r.errored) {
                    return;
                }

                // the model element will be null for missing types. Additionally, we don't want the system classpath
                // in our tree, because that is superfluous.
                if (r.modelElement != null && (r.modelElement.getArchive() == null
                        || !r.modelElement.getArchive().getName().equals(SYSTEM_CLASSPATH_NAME))) {

                    if (r.inclusionState.getMatch().toBoolean(true)) {
                        placeInTree(r);
                        r.inTree = true;
                        r.modelElement.setRawUseSites(r.useSites);
                        r.modelElement.setRawUsedTypes(r.usedTypes.entrySet().stream().collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().entrySet().stream()
                                        .filter(typeE -> typeE.getKey().modelElement != null).collect(Collectors
                                                .toMap(typeE -> typeE.getKey().modelElement, Map.Entry::getValue)))));
                        types.add(r);
                        environment.setSuperTypes(r.javacElement,
                                r.superTypes.stream().map(tr -> tr.javacElement).collect(toList()));
                        r.modelElement.setInApi(r.inApi);
                        r.modelElement.setInApiThroughUse(r.inApiThroughUse);
                    }
                }
            });

            return types;
        }

        private void determineApiStatus() {
            Set<TypeRecord> undetermined = new HashSet<>(this.types.values());
            while (!undetermined.isEmpty()) {
                undetermined = undetermined.stream().filter(tr -> tr.inclusionState.getMatch().toBoolean(true))
                        .filter(tr -> tr.inApi)
                        .flatMap(tr -> tr.usedTypes.entrySet().stream()
                                .map(e -> new AbstractMap.SimpleImmutableEntry<>(tr, e)))
                        .filter(e -> movesToApi(e.getValue().getKey()))
                        .flatMap(e -> e.getValue().getValue().keySet().stream()).filter(usedTr -> !usedTr.inApi)
                        .filter(usedTr -> usedTr.inclusionState.getMatch().toBoolean(true)).peek(usedTr -> {
                            usedTr.inApi = true;
                            usedTr.inApiThroughUse = true;
                        }).collect(toSet());
            }
        }

        private void moveInnerClassesOfPrimariesToApi() {
            Set<TypeRecord> primaries = this.types.values().stream().filter(tr -> tr.primaryApi).filter(tr -> tr.inApi)
                    .filter(tr -> tr.nestingDepth == 0).collect(toSet());

            while (!primaries.isEmpty()) {
                primaries = primaries.stream()
                        .flatMap(tr -> tr.usedTypes.getOrDefault(UseSite.Type.CONTAINS, emptyMap()).keySet().stream())
                        .filter(containedTr -> containedTr.inclusionState.getMatch().toBoolean(true))
                        .filter(containedTr -> containedTr.modelElement != null)
                        .filter(containedTr -> !shouldBeIgnored(containedTr.modelElement.getDeclaringElement()))
                        .peek(containedTr -> containedTr.inApi = true).collect(toSet());
            }
        }

        private void removeAnnotatesUses() {
            Map<TypeElement, Boolean> newTypes = requiredTypes.entrySet().stream().filter(e -> {
                boolean isAnno = e.getValue();
                if (isAnno) {
                    this.types.get(e.getKey()).useSites.clear();
                }
                return !isAnno;
            }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
            requiredTypes.clear();
            requiredTypes.putAll(newTypes);
        }

        private void initChildren() {
            List<TypeRecord> copy;
            do {
                copy = new ArrayList<>(this.types.values());
                copy.forEach(this::initChildren);
            } while (this.types.size() != copy.size());
        }

        private void initChildren(TypeRecord tr) {
            if (tr.modelElement == null) {
                tr.inheritableElements = Collections.emptySet();
                return;
            }

            if (tr.inheritableElements != null) {
                return;
            }

            tr.inheritableElements = new HashSet<>(4);

            // the set of methods' override-sensitive signatures - I.e. this is the list of methods
            // actually visible on a type.
            Set<String> methods = new HashSet<>(8);

            Consumer<JavaElementBase<?, ?>> addOverride = e -> {
                if (e instanceof MethodElement) {
                    MethodElement me = (MethodElement) e;
                    methods.add(getOverrideMapKey(me));
                }
            };

            Consumer<JavaElementBase<?, ?>> initChildren = e -> {
                FilterStartResult fr = filter.start(e);
                if (fr.getDescend().toBoolean(true)) {
                    initNonClassElementChildren(tr, e, false);
                }
                finishFiltering(tr, e);
            };

            // add declared stuff
            tr.accessibleDeclaredNonClassMembers.stream()
                    .map(e -> elementFor(e, e.asType(), environment, tr.modelElement.getArchive())).peek(addOverride)
                    .peek(c -> {
                        tr.modelElement.getChildren().add(c);
                        tr.inheritableElements.add(c);
                    }).forEach(initChildren);

            tr.inaccessibleDeclaredNonClassMembers.stream()
                    .map(e -> elementFor(e, e.asType(), environment, tr.modelElement.getArchive())).peek(addOverride)
                    .peek(c -> tr.modelElement.getChildren().add(c)).forEach(initChildren);

            // now add inherited stuff
            tr.superTypes.forEach(str -> addInherited(tr, str, methods));

            // and finally the annotations
            for (AnnotationMirror m : tr.javacElement.getAnnotationMirrors()) {
                tr.modelElement.getChildren().add(new AnnotationElement(environment, tr.modelElement.getArchive(), m));
            }
        }

        private void addInherited(TypeRecord target, TypeRecord superType, Set<String> methodOverrideMap) {
            Types types = environment.getTypeUtils();

            if (superType.inheritableElements == null) {
                initChildren(superType);
            }

            for (JavaElementBase<?, ?> e : superType.inheritableElements) {
                if (e instanceof MethodElement) {
                    if (!shouldAddInheritedMethodChild((MethodElement) e, methodOverrideMap)) {
                        continue;
                    }
                }

                JavaElementBase<?, ?> ret;
                if (isGeneric(e.getDeclaringElement())) {
                    // we need to generate the new element fully, because it is generic and thus can have
                    // a different signature in the target type than it has in the supertype.
                    TypeMirror elementType = types.asMemberOf((DeclaredType) target.javacElement.asType(),
                            e.getDeclaringElement());

                    ret = JavaElementFactory.elementFor(e.getDeclaringElement(), elementType, environment,
                            target.modelElement.getArchive());

                    if (!target.modelElement.getChildren().add(ret)) {
                        continue;
                    }

                    FilterStartResult fr = filter.start(ret);
                    if (fr.getDescend().toBoolean(true)) {
                        initNonClassElementChildren(target, ret, true);
                    }

                    finishFiltering(target, ret);
                } else {
                    // this element is not generic, so we can merely copy it...
                    if (target.inApi) {
                        // this element will for sure be in the API and hence API checked... let's optimize
                        // this case and pre-create the comparable signature of the element before we copy it
                        // so that we don't have to re-create it in every inherited class. This is especially
                        // useful for methods from java.lang.Object, and in deep large hierarchies.
                        InitializationOptimizations.initializeComparator(e);
                    }

                    @SuppressWarnings({ "unchecked", "rawtypes" })
                    Optional<JavaElementBase<?, ?>> copy = ((JavaElementBase) e).cloneUnder(target.modelElement);
                    if (!copy.isPresent()) {
                        continue;
                    }

                    ret = copy.get();
                    // the cloned element needs to look like it originated in the archive of the target.
                    ret.setArchive(target.modelElement.getArchive());

                    FilterStartResult fr = filter.start(ret);
                    if (fr.getDescend().toBoolean(true)) {
                        copyInheritedNonClassElementChildren(target, ret, e.getChildren(), target.inApi);
                    }

                    finishFiltering(target, ret);
                }

                addUseSites(target, ret);

                ret.setInherited(true);
            }

            for (TypeRecord st : superType.superTypes) {
                addInherited(target, st, methodOverrideMap);
            }
        }

        private void addUseSites(TypeRecord target, JavaElementBase<?, ?> targetEl) {
            if (targetEl instanceof FieldElement) {
                addFieldUseSites(target, (FieldElement) targetEl);
            } else if (targetEl instanceof MethodElement) {
                addMethodUseSites(target, (MethodElement) targetEl);
            }
        }

        private void addFieldUseSites(TypeRecord target, FieldElement targetF) {
            TypeMirror usedType = targetF.getModelRepresentation();
            TypeElement usedEl = getTypeElement.visit(usedType);
            if (usedEl == null) {
                return;
            }

            VariableElement f = targetF.getDeclaringElement();

            UseSiteConstructor ctor = (useType, site, indexInParent) -> new InheritedUseSite(useType, site,
                    target.modelElement, indexInParent);

            addUse(target, f, usedEl, UseSite.Type.HAS_TYPE, -1, ctor);
            addTypeParamUses(target, f, usedType, t -> addUse(target, f, t, TYPE_PARAMETER_OR_BOUND, -1, ctor));
        }

        private void addMethodUseSites(TypeRecord target, MethodElement targetM) {
            UseSiteConstructor ctor = (useType, site, indexInParent) -> new InheritedUseSite(useType, site,
                    target.modelElement, indexInParent);

            ExecutableType methodType = targetM.getModelRepresentation();
            ExecutableElement method = targetM.getDeclaringElement();

            TypeMirror retType = methodType.getReturnType();
            TypeElement retEl = getTypeElement.visit(retType);
            if (retEl != null) {
                addUse(target, method, retEl, RETURN_TYPE, -1, ctor);
                addTypeParamUses(target, method, retType,
                        v -> addUse(target, method, v, TYPE_PARAMETER_OR_BOUND, -1, ctor));
            }

            int i = 0;
            for (TypeMirror p : methodType.getParameterTypes()) {
                TypeElement pEl = getTypeElement.visit(p);
                if (pEl != null) {
                    addUse(target, targetM.getDeclaringElement(), pEl, PARAMETER_TYPE, i++, ctor);
                    addTypeParamUses(target, method, p,
                            v -> addUse(target, method, v, TYPE_PARAMETER_OR_BOUND, -1, ctor));
                }
            }

            for (TypeMirror t : methodType.getThrownTypes()) {
                addUse(target, targetM.getDeclaringElement(), getTypeElement.visit(t), IS_THROWN, i++, ctor);
                addTypeParamUses(target, method, t, v -> addUse(target, method, v, TYPE_PARAMETER_OR_BOUND, -1, ctor));
            }
        }

        private void finishFiltering(TypeRecord elementOwner, JavaElementBase<?, ?> el) {
            // conform to the TreeFilter contract
            FilterFinishResult finish = filter.finish(el);

            if (el instanceof MethodParameterElement) {
                // we don't ever exclude method parameters from analysis.. that just doesn't make sense from the Java
                // language perspective...
                return;
            }

            JavaModelElement parent = el.getParent();

            if (finish.getMatch() == Ternary.FALSE && parent != null) {
                removeFromUseSites(elementOwner, el);
                parent.getChildren().remove(el);
            }

            if (el instanceof org.revapi.java.model.TypeElement) {
                TypeRecord tr = getTypeRecord((TypeElement) el.getDeclaringElement());
                tr.inclusionState = tr.inclusionState.withMatch(finish.getMatch());
                if (tr == elementOwner) {
                    return;
                }
            }

            if (!finish.isInherited() && finish.getMatch().toBoolean(true)) {
                while (elementOwner != null) {
                    elementOwner.inclusionState = elementOwner.inclusionState
                            .or(FilterStartResult.from(finish, elementOwner.inclusionState.getDescend()));
                    elementOwner = elementOwner.parent;
                }
            }
        }

        /**
         * This needs to be called PRIOR TO removing the element from the children of its parent.
         */
        private void removeFromUseSites(TypeRecord elementOwner, JavaElementBase<?, ?> el) {
            // first clean up the used types of the elementOwner - anything used by the el should be removed
            elementOwner.usedTypes.entrySet().removeIf(useSiteTypeEntry -> {
                useSiteTypeEntry.getValue().entrySet().removeIf(sitesByOwnerEntry -> {
                    sitesByOwnerEntry.getValue().removeIf(site -> site.useSite == el.getDeclaringElement());
                    return sitesByOwnerEntry.getValue().isEmpty();
                });

                return useSiteTypeEntry.getValue().isEmpty();
            });

            // next, if el is a type, we need to clean up its usesites - none of them uses it any longer
            if (el instanceof org.revapi.java.model.TypeElement) {
                TypeRecord tr = getTypeRecord((TypeElement) el.getDeclaringElement());
                for (ClassPathUseSite site : tr.useSites) {
                    TypeRecord owner = getOwner(site);
                    if (owner != null) {
                        owner.usedTypes.entrySet().removeIf(useSiteTypeEntry -> {
                            useSiteTypeEntry.getValue().entrySet().removeIf(sitesByOwnerEntry -> {
                                sitesByOwnerEntry.getValue().removeIf(s -> s.useSite == site.site);
                                return sitesByOwnerEntry.getValue().isEmpty();
                            });

                            return useSiteTypeEntry.getValue().isEmpty();
                        });
                    }
                }

                tr.useSites.clear();
            }
        }

        private TypeRecord getOwner(ClassPathUseSite site) {
            if (site instanceof InheritedUseSite) {
                return getTypeRecord(((InheritedUseSite) site).inheritor.getDeclaringElement());
            } else {
                Element el = site.site;
                while (el != null && !(el instanceof TypeElement)) {
                    el = el.getEnclosingElement();
                }

                if (el == null) {
                    return null;
                } else {
                    return getTypeRecord((TypeElement) el);
                }
            }
        }

        private boolean shouldAddInheritedMethodChild(MethodElement methodElement, Set<String> overrideMap) {
            if (methodElement.getDeclaringElement().getKind() == ElementKind.CONSTRUCTOR) {
                return false;
            }
            String overrideKey = getOverrideMapKey(methodElement);
            boolean alreadyIncludedMethod = overrideMap.contains(overrideKey);
            if (alreadyIncludedMethod) {
                return false;
            } else {
                // remember this to check if the next super type doesn't declare a method this one overrides
                overrideMap.add(overrideKey);
                return true;
            }
        }

        private void initNonClassElementChildren(TypeRecord parentOwner, JavaElementBase<?, ?> parent,
                boolean inherited) {
            Types types = environment.getTypeUtils();

            List<? extends Element> children = parent.getDeclaringElement()
                    .accept(new SimpleElementVisitor8<List<? extends Element>, Void>() {
                        @Override
                        protected List<? extends Element> defaultAction(Element e, Void aVoid) {
                            return Collections.emptyList();
                        }

                        @Override
                        public List<? extends Element> visitType(TypeElement e, Void aVoid) {
                            return e.getEnclosedElements();
                        }

                        @Override
                        public List<? extends Element> visitExecutable(ExecutableElement e, Void aVoid) {
                            return e.getParameters();
                        }
                    }, null);

            int idx = 0;
            for (Element child : children) {
                if (child.getKind().isClass() || child.getKind().isInterface()) {
                    continue;
                }

                TypeMirror representation;
                if ((child.getKind() == ElementKind.METHOD || child.getKind() == ElementKind.CONSTRUCTOR)
                        && isGeneric(child)) {
                    representation = types.asMemberOf(parentOwner.modelElement.getModelRepresentation(), child);
                } else if (child.getKind() == ElementKind.PARAMETER) {
                    ExecutableType methodType = (ExecutableType) parent.getModelRepresentation();
                    representation = methodType.getParameterTypes().get(idx);
                } else {
                    representation = child.asType();
                }

                JavaElementBase<?, ?> childEl = JavaElementFactory.elementFor(child, representation, environment,
                        parent.getArchive());

                childEl.setInherited(inherited);

                parent.getChildren().add(childEl);

                FilterStartResult fr = filter.start(childEl);
                if (fr.getDescend().toBoolean(true)) {
                    initNonClassElementChildren(parentOwner, childEl, inherited);
                }

                finishFiltering(parentOwner, childEl);
                idx++;
            }

            for (AnnotationMirror m : parent.getDeclaringElement().getAnnotationMirrors()) {
                parent.getChildren().add(new AnnotationElement(environment, parent.getArchive(), m));
            }
        }

        @SuppressWarnings("EqualsWithItself")
        private void copyInheritedNonClassElementChildren(TypeRecord parentOwner, JavaElementBase<?, ?> parent,
                Iterable<? extends JavaElement> sourceChildren, boolean forceComparatorInitialization) {

            for (JavaElement c : sourceChildren) {
                if (c instanceof TypeElement) {
                    continue;
                }

                if (forceComparatorInitialization) {
                    InitializationOptimizations.initializeComparator(c);
                }

                JavaElement cc = InitializationOptimizations.clone(c);

                parent.getChildren().add(cc);

                if (cc instanceof AbstractJavaElement) {
                    ((AbstractJavaElement) cc).setArchive(parent.getArchive());

                    if (cc instanceof JavaElementBase) {
                        JavaElementBase<?, ?> mcc = (JavaElementBase<?, ?>) cc;
                        mcc.setInherited(true);
                        copyInheritedNonClassElementChildren(parentOwner, mcc, c.getChildren(),
                                forceComparatorInitialization);
                    }
                }
            }
        }
    }

    private static String getOverrideMapKey(MethodElement method) {
        return InitializationOptimizations.getMethodComparisonKey(method);
    }

    private static boolean isGeneric(Element element) {
        return element.accept(IS_TYPE_PARAMETERIZED, null);
    }

    private static final class ArchiveLocation implements JavaFileManager.Location {
        private final Archive archive;

        private ArchiveLocation(Archive archive) {
            this.archive = archive;
        }

        Archive getArchive() {
            return archive;
        }

        @Override
        public String getName() {
            return "archiveLocation_" + archive.getName();
        }

        @Override
        public boolean isOutputLocation() {
            return false;
        }
    }

    private static final class SyntheticLocation implements JavaFileManager.Location {
        private final String name = "randomName" + (new Random().nextInt());

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isOutputLocation() {
            return false;
        }
    }

    private static final class TypeRecord {
        Set<ClassPathUseSite> useSites = new HashSet<>(2);
        TypeElement javacElement;
        org.revapi.java.model.TypeElement modelElement;
        Map<UseSite.Type, Map<TypeRecord, Set<UseSitePath>>> usedTypes = new EnumMap<>(UseSite.Type.class);
        Set<Element> accessibleDeclaredNonClassMembers = new HashSet<>(4);
        Set<Element> inaccessibleDeclaredNonClassMembers = new HashSet<>(4);
        Set<JavaElementBase<?, ?>> inheritableElements;
        // important for this to be a linked hashset so that superclasses are processed prior to implemented interfaces
        Set<TypeRecord> superTypes = new LinkedHashSet<>(2);
        FilterStartResult inclusionState = FilterStartResult.defaultResult();
        TypeRecord parent;
        boolean inApi;
        boolean inApiThroughUse;
        boolean primaryApi;
        int nestingDepth;
        boolean errored;
        boolean inTree;

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("TypeRecord[");
            sb.append("inApi=").append(inApi);
            sb.append(", modelElement=").append(modelElement);
            sb.append(']');
            return sb.toString();
        }
    }

    private static boolean movesToApi(UseSite.Type useType) {
        return useType.isMovingToApi();
    }

    static boolean shouldBeIgnored(Element element) {
        return Collections.disjoint(element.getModifiers(), ACCESSIBLE_MODIFIERS);
    }

    @FunctionalInterface
    private interface UseSiteConstructor {
        ClassPathUseSite create(UseSite.Type useType, Element site, int indexInParent);
    }
}
