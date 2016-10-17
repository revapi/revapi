package org.revapi.java.compilation;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

import org.revapi.Archive;
import org.revapi.java.AnalysisConfiguration;
import org.revapi.java.model.MissingClassElement;
import org.revapi.java.spi.UseSite;
import org.revapi.java.spi.Util;
import org.revapi.query.Filter;

/**
 * @author Lukas Krejci
 * @since 0.11.0
 */
final class ClasspathScanner {
    private static final List<Modifier> ACCESSIBLE_MODIFIERS = Arrays.asList(Modifier.PUBLIC, Modifier.PROTECTED);

    private final StandardJavaFileManager fileManager;
    private final ProbingEnvironment environment;
    private final Map<Archive, File> classPath;
    private final Map<Archive, File> additionalClassPath;
    private final AnalysisConfiguration.MissingClassReporting missingClassReporting;
    private final boolean ignoreMissingAnnotations;
    private final InclusionFilter inclusionFilter;
    private final boolean defaultInclusionCase;

    ClasspathScanner(StandardJavaFileManager fileManager, ProbingEnvironment environment,
                     Map<Archive, File> classPath, Map<Archive, File> additionalClassPath,
                     AnalysisConfiguration.MissingClassReporting missingClassReporting,
                     boolean ignoreMissingAnnotations, InclusionFilter inclusionFilter) {
        this.fileManager = fileManager;
        this.environment = environment;
        this.classPath = classPath;
        this.additionalClassPath = additionalClassPath;
        this.missingClassReporting = missingClassReporting == null
                ? AnalysisConfiguration.MissingClassReporting.ERROR
                : missingClassReporting;
        this.ignoreMissingAnnotations = ignoreMissingAnnotations;
        this.inclusionFilter = inclusionFilter;
        this.defaultInclusionCase = inclusionFilter.defaultCase();
    }

    void initTree() throws IOException {
        List<ArchiveLocation> classPathLocations = classPath.keySet().stream().map(ArchiveLocation::new)
                .collect(toList());

        Scanner scanner = new Scanner();

        for (ArchiveLocation loc : classPathLocations) {
            scanner.scan(loc, classPath.get(loc.getArchive()));
        }

        Set<TypeElement> lastUnknowns = Collections.emptySet();

        Map<String, ArchiveLocation> cachedArchives = new HashMap<>(additionalClassPath.size());

        while (!scanner.requiredTypes.isEmpty() && !lastUnknowns.equals(scanner.requiredTypes.keySet())) {
            lastUnknowns = new HashSet<>(scanner.requiredTypes.keySet());
            for (TypeElement t : lastUnknowns) {
                try {
                    Field f = t.getClass().getField("classfile");
                    JavaFileObject jfo = (JavaFileObject) f.get(t);
                    if (jfo == null) {
                        t = environment.getElementUtils().getTypeElement(t.getQualifiedName());
                        if (t == null) {
                            //this type is really missing...
                            continue;
                        }
                        jfo = (JavaFileObject) f.get(t);
                    }
                    URI uri = jfo.toUri();
                    String path;
                    if ("jar".equals(uri.getScheme())) {
                        uri = URI.create(uri.getSchemeSpecificPart());
                        path = uri.getPath().substring(0, uri.getPath().lastIndexOf('!'));
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
                        scanner.scanClass(loc, t);
                    }

                } catch (NoSuchFieldException e) {
                    //TODO fallback to manually looping through archives
                } catch (IllegalAccessException e) {
                    //should not happen
                    throw new AssertionError("Illegal access after setAccessible(true) on a field. Wha?", e);
                }
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
        };

        void scan(ArchiveLocation location, File path) throws IOException {
            fileManager.setLocation(location, Collections.singleton(path));

            Iterable<? extends JavaFileObject> jfos = fileManager.list(location, "",
                    EnumSet.of(JavaFileObject.Kind.CLASS), true);

            for (JavaFileObject jfo : jfos) {
                TypeElement type = Util.findTypeByBinaryName(environment.getElementUtils(),
                        fileManager.inferBinaryName(location, jfo));

                //type can be null if it represents an anonymous or member class...
                if (type != null) {
                    scanClass(location, type);
                }
            }
        }

        void scanClass(ArchiveLocation loc, TypeElement type) {
            if (processed.contains(type)) {
                return;
            }

            processed.add(type);
            requiredTypes.remove(type);

            String bn = environment.getElementUtils().getBinaryName(type).toString();
            String cn = type.getQualifiedName().toString();
            boolean includes = inclusionFilter.accepts(bn, cn);
            boolean excludes = inclusionFilter.rejects(bn, cn);

            //type.asType() possibly not completely correct when dealing with inner class of a parameterized class
            org.revapi.java.model.TypeElement t =
                    new org.revapi.java.model.TypeElement(environment, loc.getArchive(), type, (DeclaredType) type.asType());

            TypeRecord tr = getTypeRecord(type);
            tr.inApi = !excludes && (tr.inApi || !shouldBeIgnored(type));
            tr.modelElement = t;
            tr.explicitlyExcluded = excludes;
            tr.explicitlyIncluded = includes;

            if (tr.explicitlyExcluded) {
                return;
            }

            TypeElement superType = getTypeElement.visit(type.getSuperclass());
            if (superType != null) {
                TypeRecord superTypeRecord = getTypeRecord(superType);
                superTypeRecord.subClasses.add(type);
                addUse(tr, type, superType, UseSite.Type.IS_INHERITED);
                if (!processed.contains(superType)) {
                    requiredTypes.put(superType, false);
                }
            }

            type.getInterfaces().stream().map(getTypeElement::visit)
                    .forEach(e -> {
                        if (!processed.contains(e)) {
                            requiredTypes.put(e, false);
                        }
                        addUse(tr, type, e, UseSite.Type.IS_IMPLEMENTED);
                    });

            for (Element e : type.getEnclosedElements()) {
                switch (e.getKind()) {
                    case ANNOTATION_TYPE:
                    case CLASS:
                    case ENUM:
                    case INTERFACE:
                        addUse(tr, type, (TypeElement) e, UseSite.Type.CONTAINS);
                        scanClass(loc, (TypeElement) e);
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

            type.getAnnotationMirrors().forEach(a -> scanAnnotation(tr, type, a));
        }

        void placeInTree(TypeRecord typeRecord) {
            TypeElement type = typeRecord.modelElement.getDeclaringElement();

            if (!(type.getEnclosingElement() instanceof TypeElement)) {
                environment.getTree().getRootsUnsafe().add(typeRecord.modelElement);
            } else {
                ArrayDeque<String> nesting = new ArrayDeque<>();
                type = (TypeElement) type.getEnclosingElement();
                while (type != null) {
                    nesting.push(type.getQualifiedName().toString());
                    type = type.getEnclosingElement() instanceof TypeElement
                            ? (TypeElement) type.getEnclosingElement()
                            : null;
                }

                Function<String, Filter<org.revapi.java.model.TypeElement>> findByCN =
                        cn -> Filter.flat(e -> cn.equals(e.getCanonicalName()));

                List<org.revapi.java.model.TypeElement> parents = Collections.emptyList();
                while (parents.isEmpty() && !nesting.isEmpty()) {
                    parents = environment.getTree().searchUnsafe(
                            org.revapi.java.model.TypeElement.class, false, findByCN.apply(nesting.pop()), null);
                }

                org.revapi.java.model.TypeElement parent = parents.isEmpty() ? null : parents.get(0);
                while (!nesting.isEmpty()) {
                    String cn = nesting.pop();
                    parents = environment.getTree().searchUnsafe(
                            org.revapi.java.model.TypeElement.class, false, findByCN.apply(cn),
                            parent);
                    if (parents.isEmpty()) {
                        //we found a "gap" in the parents included in the model. let's start from the top
                        //again
                        do {
                            parents = environment.getTree().searchUnsafe(
                                    org.revapi.java.model.TypeElement.class, false, findByCN.apply(cn), null);
                            if (parents.isEmpty() && !nesting.isEmpty()) {
                                cn = nesting.pop();
                            } else {
                                break;
                            }
                        } while (!nesting.isEmpty());
                    }

                    parent = parents.isEmpty() ? null : parents.get(0);
                }

                if (parent == null) {
                    environment.getTree().getRootsUnsafe().add(typeRecord.modelElement);
                } else {
                    parent.getChildren().add(typeRecord.modelElement);
                }
            }
        }

        void scanField(TypeRecord owningType, VariableElement field) {
            if (shouldBeIgnored(field)) {
                return;
            }

            TypeElement fieldType = field.asType().accept(getTypeElement, null);

            //fieldType == null means primitive type, if no type can be found, exception is thrown
            if (fieldType == null) {
                return;
            }


            addUse(owningType, field, fieldType, UseSite.Type.HAS_TYPE);
            addType(fieldType, false);

            field.getAnnotationMirrors().forEach(a -> scanAnnotation(owningType, field, a));
        }

        void scanMethod(TypeRecord owningType, ExecutableElement method) {
            if (shouldBeIgnored(method)) {
                return;
            }

            TypeElement returnType = method.getReturnType().accept(getTypeElement, null);
            if (returnType != null) {
                addUse(owningType, method, returnType, UseSite.Type.RETURN_TYPE);
                addType(returnType, false);
                addToApiIfNotExcluded(returnType);
            }

            int idx = 0;
            for (VariableElement p : method.getParameters()) {
                TypeElement pt = p.asType().accept(getTypeElement, null);
                if (pt != null) {
                    addUse(owningType, method, pt, UseSite.Type.PARAMETER_TYPE, idx++);
                    addType(pt, false);
                    addToApiIfNotExcluded(pt);
                }

                p.getAnnotationMirrors().forEach(a -> scanAnnotation(owningType, p, a));
            }

            method.getThrownTypes().forEach(t -> {
                TypeElement ex = t.accept(getTypeElement, null);
                if (ex != null) {
                    addUse(owningType, method, ex, UseSite.Type.IS_THROWN);
                    addType(ex, false);
                }

                t.getAnnotationMirrors().forEach(a -> scanAnnotation(owningType, method, a));
            });

            method.getAnnotationMirrors().forEach(a -> scanAnnotation(owningType, method, a));
        }

        private void addToApiIfNotExcluded(TypeElement t) {
            TypeRecord tr = getTypeRecord(t);
            if (!tr.explicitlyExcluded) {
                tr.inApi = true;
            }
        }

        void scanAnnotation(TypeRecord owningType, Element annotated, AnnotationMirror annotation) {
            TypeElement type = annotation.getAnnotationType().accept(getTypeElement, null);
            if (type != null) {
                addUse(owningType, annotated, type, UseSite.Type.ANNOTATES);
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

        boolean shouldBeIgnored(Element element) {
            return Collections.disjoint(element.getModifiers(), ACCESSIBLE_MODIFIERS);
        }

        void addUse(TypeRecord userType, Element user, TypeElement used, UseSite.Type useType) {
            addUse(userType, user, used, useType, -1);
        }

        void addUse(TypeRecord userType, Element user, TypeElement used, UseSite.Type useType, int indexInParent) {
            TypeRecord usedTr = getTypeRecord(used);
            Set<ClassPathUseSite> sites = usedTr.useSites;
            sites.add(new ClassPathUseSite(useType, user, indexInParent));

            Set<TypeRecord> usedTypes = userType.usedTypes.get(useType);
            if (usedTypes == null) {
                usedTypes = new HashSet<>(4);
                userType.usedTypes.put(useType, usedTypes);
            }

            usedTypes.add(usedTr);
        }

        TypeRecord getTypeRecord(TypeElement type) {
            TypeRecord rec = types.get(type);
            if (rec == null) {
                rec = new TypeRecord();
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
            Map<TypeElement, org.revapi.java.model.TypeElement> types = new IdentityHashMap<>();

            if (ignoreMissingAnnotations && !requiredTypes.isEmpty()) {
                Map<TypeElement, Boolean> newTypes = requiredTypes.entrySet().stream().filter(e -> {
                    boolean isAnno = e.getValue();
                    if (isAnno) {
                        this.types.get(e.getKey()).useSites.clear();
                    }
                    return !isAnno;
                }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                requiredTypes.clear();
                requiredTypes.putAll(newTypes);
            }

            Elements els = environment.getElementUtils();

            Set<TypeElement> ignored = new HashSet<>();
            Comparator<Map.Entry<TypeElement, TypeRecord>> byNestingDepth = (a, b) -> {
                TypeRecord ar = a.getValue();
                TypeRecord br = b.getValue();
                int ret = ar.nestingDepth - br.nestingDepth;
                if (ret == 0) {
                    ret = a.getKey().getQualifiedName().toString().compareTo(b.getKey().getQualifiedName().toString());
                }

                //the less nested classes need to come first
                return ret;
            };

            //set up the subclasses of the types so that we can determine the accessibility of methods, etc.
            this.types.values().stream()
                    .filter(r -> r.modelElement != null)
                    .forEach(r -> {
                        r.modelElement.setSubClasses(r.subClasses.stream()
                                .map(te -> this.types.get(te).modelElement)
                                .filter(c -> c != null)
                                .collect(Collectors.toSet()));
                    });

            //determine the inAPi status of all type records now that we have a complete picture of who uses what
            Set<TypeRecord> undetermined = new HashSet<>(this.types.values());
            while (!undetermined.isEmpty()) {
                undetermined = undetermined.stream()
                        .filter(tr -> tr.modelElement != null && !tr.explicitlyExcluded)
                        .filter(tr -> tr.inApi /* || tr.modelElement.isMembersAccessible()*/) //the member accessibility is not a completely correct thing to check here. It is much simpler to check all the inherited members with each class than to compute accessibility at the parent level.
                        .flatMap(tr -> tr.usedTypes.entrySet().stream())
                        .filter(e -> movesToApi(e.getKey()))
                        .flatMap(e -> e.getValue().stream())
                        .filter(usedTr -> !usedTr.inApi)
                        .map(usedTr -> {
                            usedTr.inApi = true;
                            return usedTr;
                        })
                        .collect(Collectors.toSet());
            }

            this.types.entrySet().stream().sorted(byNestingDepth).forEach(e -> {
                TypeElement t = e.getKey();
                TypeRecord r = e.getValue();

                //the model element will be null for types from the bootstrap classpath
                if (r.modelElement != null) {
                    String cn = t.getQualifiedName().toString();
                    boolean includes = r.explicitlyIncluded;
                    boolean excludes = r.explicitlyExcluded;

                    if (includes) {
                        environment.addExplicitInclusion(cn);
                    }

                    if (excludes) {
                        environment.addExplicitExclusion(cn);
                        ignored.add(t);
                    } else {
                        boolean include = defaultInclusionCase || includes;
                        Element owner = t.getEnclosingElement();

                        if (owner != null && owner instanceof TypeElement) {
                            ArrayDeque<TypeElement> owners = new ArrayDeque<>();
                            while (owner != null && owner instanceof TypeElement) {
                                owners.push((TypeElement) owner);
                                owner = owner.getEnclosingElement();
                            }

                            //find the first owning class that is part of our model
                            List<TypeElement> siblings = environment.getTree().getRootsUnsafe().stream().map(
                                    org.revapi.java.model.TypeElement::getDeclaringElement).collect(Collectors.toList());

                            while (!owners.isEmpty()) {
                                if (ignored.contains(owners.peek()) || siblings.contains(owners.peek())) {
                                    break;
                                }
                                owners.pop();
                            }

                            //if the user doesn't want this type included explicitly, we need to check in the parents
                            //if some of them wasn't explicitly excluded
                            if (!includes && !owners.isEmpty()) {
                                do {
                                    TypeElement o = owners.pop();
                                    include = !ignored.contains(o) && siblings.contains(o);
                                    siblings = ElementFilter.typesIn(o.getEnclosedElements());
                                } while (include && !owners.isEmpty());
                            }
                        }

                        if (include) {
                            placeInTree(r);
                            r.modelElement.setRawUseSites(r.useSites);
                            types.put(t, r.modelElement);
                            r.modelElement.setInApi(r.inApi);
                        }
                    }
                }
            });

            if (!requiredTypes.isEmpty()) {
                switch (missingClassReporting) {
                    case ERROR:
                        List<String> reallyMissing = requiredTypes.keySet().stream()
                                .map(t -> els.getTypeElement(t.getQualifiedName()))
                                .filter(t -> els.getTypeElement(t.getQualifiedName()) == null)
                                .map(t -> t.getQualifiedName().toString())
                                .sorted()
                                .collect(toList());

                        if (!reallyMissing.isEmpty()) {
                            throw new IllegalStateException(
                                    "The following classes that contribute to the public API of " +
                                            environment.getApi() +
                                            " could not be located: " +
                                            reallyMissing);
                        }
                        break;
                    case REPORT:
                        for (TypeElement t : requiredTypes.keySet()) {
                            TypeElement type = els.getTypeElement(t.getQualifiedName());
                            if (type == null) {
                                String bin = els.getBinaryName(t).toString();
                                MissingClassElement mce = new MissingClassElement(environment, bin,
                                        t.getQualifiedName().toString());
                                types.put(mce.getDeclaringElement(), mce);
                                environment.getTree().getRootsUnsafe().add(mce);
                            }
                        }
                }
            }

            environment.setTypeMap(types);
       }
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

    private static final class TypeRecord {
        Set<ClassPathUseSite> useSites = new HashSet<>(4);
        org.revapi.java.model.TypeElement modelElement;
        Set<TypeElement> subClasses = new HashSet<>(1);
        Map<UseSite.Type, Set<TypeRecord>> usedTypes = new EnumMap<>(UseSite.Type.class);
        boolean explicitlyExcluded;
        boolean explicitlyIncluded;
        boolean inApi;
        int nestingDepth;

        @Override public String toString() {
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
}
