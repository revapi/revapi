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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.annotation.Nullable;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.revapi.Archive;
import org.revapi.java.AnalysisConfiguration;
import org.revapi.java.model.MissingClassElement;
import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.UseSite;
import org.revapi.query.Filter;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
final class ClassTreeInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(ClassTreeInitializer.class);

    private final ProbingEnvironment environment;
    private final AnalysisConfiguration.MissingClassReporting reporting;
    private final boolean ignoreMissingAnnotations;
    private final Set<File> bootstrapClasspath;
    private final HashMap<String, TypeElement> typeCache;
    private HashSet<String> bootstrapClasses;
    private final boolean ignoreAdditionalArchivesContributions;

    public ClassTreeInitializer(ProbingEnvironment environment,
        AnalysisConfiguration.MissingClassReporting missingClassReporting, boolean ignoreMissingAnnotations,
        Set<File> bootstrapClasspath, boolean ignoreAdditionalArchivesContributions) {
        this.environment = environment;
        this.reporting = missingClassReporting;
        this.ignoreMissingAnnotations = ignoreMissingAnnotations;
        this.typeCache = new HashMap<>();
        this.bootstrapClasspath = bootstrapClasspath;
        this.ignoreAdditionalArchivesContributions = ignoreAdditionalArchivesContributions;
    }

    public void initTree() throws IOException {
        InitTreeContext context = new InitTreeContext();

        long time = System.currentTimeMillis();

        for (Archive a : environment.getApi().getArchives()) {
            LOG.trace("Processing archive {}", a.getName());
            processArchive(a, context);
        }

        removeClassesFromRtJar(context.additionalClassBinaryNames);

        context.onlyAddAdditional = true;

        if (!context.additionalClassBinaryNames.isEmpty() && environment.getApi().getSupplementaryArchives() != null) {
            for (Archive a : environment.getApi().getSupplementaryArchives()) {
                LOG.trace("Processing archive {}", a.getName());
                processArchive(a, context);

                // check for additional class changes inside this loop so that we exit as soon as possible if we
                // clear out the additional classes early.
                if (context.additionalClassBinaryNames.isEmpty()) {
                    break;
                }
            }
        }

        if (!context.additionalClassBinaryNames.isEmpty()) {
            List<String> prettyNames = new ArrayList<>(context.additionalClassBinaryNames);
            Collections.sort(prettyNames);

            if (reporting == null || reporting == AnalysisConfiguration.MissingClassReporting.ERROR) {
                //default is to throw
                throw new IllegalStateException(
                    "The following classes that contribute to the public API of " + environment.getApi() +
                        " could not be located: " + prettyNames
                );
            }

            switch (reporting) {
            case IGNORE:
                LOG.warn("The following classes that contribute to the public API of " + environment.getApi() +
                    " could not be located: " + prettyNames);
                break;
            case REPORT:
                for (String binary : context.additionalClassBinaryNames) {
                    addConditionally(null, binary, binary, null, true, context.additionalClassBinaryNames,
                        context.dormantTypes);
                }
            }
        }

        // place the pending inner classes in their proper place in the tree.
        // Because the children of the added classes are processed using javax.lang.model utilities inside
        // TypeElement.getChildren(), we only actually need to care about inner classes whose parents are NOT
        // in the tree yet - these inner classes were detected as parts of the API but their owning classes weren't
        for (Map.Entry<String, Set<TypeElement>> innerClassesPosition : context.innerClassPositions.entrySet()) {
            String ownerBinaryName = innerClassesPosition.getKey();

            if (findByType(ownerBinaryName) == null) {
                for (TypeElement type : innerClassesPosition.getValue()) {
                    environment.getTree().getRootsUnsafe().add(type);
                }
            }
        }

        if (LOG.isInfoEnabled()) {
            time = System.currentTimeMillis() - time;
            final int[] num = new int[1];
            environment.getTree().searchUnsafe(JavaElement.class, true, new Filter<JavaElement>() {
                @Override
                public boolean applies(@Nullable JavaElement element) {
                    num[0]++;
                    return true;
                }

                @Override
                public boolean shouldDescendInto(@Nullable Object element) {
                    return true;
                }
            }, null);
            LOG.info("Tree init took " + time + "ms. The resulting tree has " + num[0] + " elements.");
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Public API class tree in {} initialized to: {}", environment.getApi(), environment.getTree());
        }
    }

    private void processArchive(Archive a, InitTreeContext context)
        throws IOException {
        if (a.getName().toLowerCase().endsWith(".jar")) {
            processJarArchive(a, context);
        } else if (a.getName().toLowerCase().endsWith(".class")) {
            processClassFile(a, context);
        }
    }

    private void processJarArchive(Archive a, InitTreeContext context) throws IOException {
        try (ZipInputStream jar = new ZipInputStream(a.openStream())) {

            ZipEntry entry = jar.getNextEntry();

            while (entry != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".class")) {
                    processClassBytes(a, jar, context);
                }

                entry = jar.getNextEntry();
            }
        }
    }

    private void processClassFile(Archive a, InitTreeContext context)
        throws IOException {
        try (InputStream data = a.openStream()) {
            processClassBytes(a, data, context);
        }
    }

    private void processClassBytes(final Archive currentArchive, InputStream data, final InitTreeContext context)
        throws IOException {

        ClassReader classReader = new ClassReader(data);

        classReader.accept(new ClassVisitor(Opcodes.ASM4) {

            private boolean isPublicAPI;
            private String visitedClassInternalName;
            private String visitedClassBinaryName;
            private String[] visitedClassOwners;
            private boolean isInnerClass;
            private TreeSet<OuterNameInnerNamePair> innerNames;
            private boolean visitedClassDormant;
            private int visitedInnerClassAccess;

            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {

                visitedClassInternalName = name;
                visitedClassBinaryName = Type.getObjectType(name).getClassName();
                isPublicAPI = isPublic(access);
                if (context.onlyAddAdditional && !isPublicAPI) {
                    isPublicAPI = context.additionalClassBinaryNames.contains(Type.getObjectType(name).getDescriptor());
                }

                visitedClassDormant =
                    context.onlyAddAdditional && !context.additionalClassBinaryNames.contains(visitedClassBinaryName);

                if (isPublicAPI) {
                    //add the superclass and interface use sites
                    reportUse(Type.getObjectType(superName), UseSite.Type.IS_INHERITED, RawUseSite.SiteType.CLASS, null,
                        null, -1);

                    for (String iface : interfaces) {
                        reportUse(Type.getObjectType(iface), UseSite.Type.IS_IMPLEMENTED, RawUseSite.SiteType.CLASS,
                            null, null, -1);
                    }
                }

                if (name.indexOf('$') >= 0) {
                    visitedClassOwners = name.split("\\$");
                }

                if (LOG.isTraceEnabled()) {
                    LOG.trace("visit(): name={}, signature={}, publicAPI={}, dormant={}, access=0x{}", name, signature,
                        isPublicAPI, visitedClassDormant, Integer.toHexString(access));
                }
            }

            @Override
            public FieldVisitor visitField(int access, final String name, final String desc, String signature,
                Object value) {
                //only consider public or protected fields - only those contribute to the API
                if (isPublicAPI && isPublic(access)) {
                    reportUse(Type.getType(desc), UseSite.Type.HAS_TYPE, RawUseSite.SiteType.FIELD, name, desc, -1);

                    return new FieldVisitor(Opcodes.ASM4) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                            reportUse(Type.getType(desc), UseSite.Type.ANNOTATES, RawUseSite.SiteType.FIELD, name, desc,
                                -1);
                            return null;
                        }
                    };
                }

                return null;
            }

            @Override
            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                LOG.trace("Visiting inner class spec {} {}", innerName, outerName);

                boolean isThisClass = visitedClassInternalName.equals(name);

                if (isThisClass) {
                    visitedInnerClassAccess = access;
                }

                isInnerClass = isInnerClass || isThisClass;

                if (isThisClass || isTransitiveOwnerOfVisitedClass(name)) {
                    if (innerNames == null) {
                        innerNames = new TreeSet<>();
                    }

                    //visiting some outer class of the currently processed class
                    innerNames.add(new OuterNameInnerNamePair(outerName, innerName));
                }
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (isPublicAPI) {
                    reportUse(Type.getType(desc), UseSite.Type.ANNOTATES, RawUseSite.SiteType.CLASS, null, null, -1);
                }

                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, final String name, final String desc, String signature,
                String[] exceptions) {

                LOG.trace("Visiting method {} {}", name, desc);

                //only consider public or protected methods - only those contribute to the API
                if (isPublicAPI && isPublic(access)) {

                    Type[] argumentTypes = Type.getArgumentTypes(desc);

                    reportUse(Type.getReturnType(desc), UseSite.Type.RETURN_TYPE, RawUseSite.SiteType.METHOD, name,
                        desc, -1);

                    //instance inner classes synthetize their constructors to always have the enclosing type as the
                    //first parameter. Ignore that parameter for usage reporting.
                    int ai = isInnerClass && "<init>".equals(name)
                        && (visitedInnerClassAccess & Opcodes.ACC_STATIC) == 0 ? 1 : 0;

                    while (ai < argumentTypes.length) {
                        Type t = argumentTypes[ai];
                        reportUse(t, UseSite.Type.PARAMETER_TYPE, RawUseSite.SiteType.METHOD_PARAMETER, name, desc,
                            ai++);
                    }

                    if (exceptions != null && exceptions.length > 0) {
                        for (String ex : exceptions) {
                            reportUse(Type.getObjectType(ex), UseSite.Type.IS_THROWN, RawUseSite.SiteType.METHOD,
                                name, desc, -1);
                        }
                    }

                    return new MethodVisitor(Opcodes.ASM4) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String annotationDesc, boolean visible) {
                            reportUse(Type.getType(annotationDesc), UseSite.Type.ANNOTATES, RawUseSite.SiteType.METHOD,
                                name, desc, -1);
                            return null;
                        }

                        @Override
                        public AnnotationVisitor visitParameterAnnotation(int parameter, String parameterDesc,
                            boolean visible) {
                            reportUse(Type.getType(parameterDesc), UseSite.Type.ANNOTATES,
                                RawUseSite.SiteType.METHOD_PARAMETER, name, desc, parameter);
                            return null;
                        }
                    };
                }

                return null;
            }

            @Override
            public void visitEnd() {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Visited {}, isAPI={}, isInner={}, onlyAdditional={}",
                        visitedClassInternalName, isPublicAPI,
                        isInnerClass, context.onlyAddAdditional);
                }

                if (context.onlyAddAdditional) {
                    LOG.trace("Found in additional API classes");
                }

                if (!isInnerClass) {
                    if (visitedClassDormant) {
                        addDormantClass(visitedClassBinaryName, visitedClassBinaryName);
                    } else {
                        addConditionally(currentArchive, visitedClassBinaryName, visitedClassBinaryName, null,
                            false, context.additionalClassBinaryNames, context.dormantTypes);
                    }
                } else {
                    String rootOwner = null;
                    List<String> owners = new ArrayList<>();

                    Iterator<OuterNameInnerNamePair> it = innerNames.iterator();
                    if (it.hasNext()) {
                        OuterNameInnerNamePair names = it.next();

                        if (names.outerName != null && names.innerName != null) {
                            //only take into account non-anonymous, non-local inner classes
                            rootOwner = names.outerName.replace('/', '.');
                            owners.add(names.innerName);
                        }

                        while (it.hasNext()) {
                            names = it.next();

                            if (names.outerName == null || names.innerName == null) {
                                //we only process non-anonymous, non-local inner classes
                                rootOwner = null;
                                break;
                            }

                            owners.add(names.innerName);
                        }

                        if (rootOwner != null) {
                            StringBuilder binaryName = new StringBuilder(rootOwner);
                            StringBuilder canonicalName = new StringBuilder(rootOwner);
                            TypeElement owningType;

                            if (context.onlyAddAdditional) {
                                for (int i = 0; i < owners.size() - 1; ++i) {
                                    String name = owners.get(i);
                                    binaryName.append('$').append(name);
                                    canonicalName.append('.').append(name);
                                }

                                String owningTypeBinaryName = binaryName.toString();
                                owningType = findByType(owningTypeBinaryName);

                                String name = owners.get(owners.size() - 1);

                                binaryName.append('$').append(name);
                                canonicalName.append('.').append(name);

                                if (owningType == null) {
                                    TypeElement innerClass;

                                    if (visitedClassDormant) {
                                        innerClass = addDormantClass(binaryName.toString(), canonicalName.toString());
                                    } else {
                                        innerClass = new TypeElement(environment, currentArchive,
                                            binaryName.toString(), canonicalName.toString());
                                        registerType(innerClass, context.additionalClassBinaryNames,
                                            context.dormantTypes);
                                    }

                                    //we need to postpone adding this inner class to the top level in the tree.
                                    //it might happen that the owning class hasn't been processed yet.
                                    //The other alternative is that the owning class is not part of the API in which
                                    //case it will never be added to the tree and the inner class will wind up at
                                    //the top level of the tree.

                                    Set<TypeElement> innerClasses = context.innerClassPositions
                                        .get(owningTypeBinaryName);
                                    if (innerClasses == null) {
                                        innerClasses = new HashSet<>();
                                        context.innerClassPositions.put(owningTypeBinaryName, innerClasses);
                                    }
                                    innerClasses.add(innerClass);
                                } else {
                                    addConditionally(currentArchive, binaryName.toString(),
                                        canonicalName.toString(), owningType, false,
                                        context.additionalClassBinaryNames, context.dormantTypes);
                                }
                            } else {
                                owningType = addConditionally(currentArchive, binaryName.toString(),
                                    canonicalName.toString(), null, false, context.additionalClassBinaryNames,
                                    context.dormantTypes);

                                for (String name : owners) {
                                    binaryName.append('$').append(name);
                                    canonicalName.append('.').append(name);

                                    owningType = addConditionally(currentArchive, binaryName.toString(),
                                        canonicalName.toString(), owningType, false,
                                        context.additionalClassBinaryNames, context.dormantTypes);
                                }
                            }
                        }
                    }
                }
            }

            private TypeElement addDormantClass(String binaryName, String canonicalName) {
                TypeElementAndUseSites tus = context.dormantTypes.get(binaryName);
                if (tus == null) {
                    tus = new TypeElementAndUseSites(
                        new TypeElement(environment, currentArchive, binaryName, canonicalName));
                    context.dormantTypes.put(binaryName, tus);
                } else if (tus.type == null) {
                    tus.type = new TypeElement(environment, currentArchive, binaryName, canonicalName);
                }

                return tus.type;
            }

            private void reportUse(Type t, UseSite.Type useType, RawUseSite.SiteType siteType, String siteName,
                String siteDescriptor, int sitePosition) {
                if (t.getSort() < Type.ARRAY) {
                    //primitive type
                    return;
                }

                if (ignoreMissingAnnotations && useType == UseSite.Type.ANNOTATES) {
                    return;
                }

                String binaryName = t.getClassName();

                if (comesFromRtJar(binaryName)) {
                    return;
                }

                if (findByType(binaryName) == null) {

                    if (!visitedClassDormant) {
                        TypeElementAndUseSites dormantType = context.dormantTypes.get(binaryName);
                        if (dormantType != null) {
                            if (dormantType.type != null && !dormantType.type.isInnerClass()) {
                                //inner classes are handled differently after all the classes have been processed
                                registerType(dormantType.type, context.additionalClassBinaryNames,
                                    context.dormantTypes);
                                environment.getTree().getRootsUnsafe().add(dormantType.type);
                            }
                            addUse(binaryName, useType, siteType, siteName, siteDescriptor, sitePosition);
                            return;
                        }
                    }

                    switch (t.getSort()) {
                    case Type.OBJECT:
                        if (!ignoreAdditionalArchivesContributions && !visitedClassDormant) {
                            context.additionalClassBinaryNames.add(binaryName);
                            LOG.trace("Adding to additional classes: {}", binaryName);
                        }
                        addUse(binaryName, useType, siteType, siteName, siteDescriptor, sitePosition);
                        break;
                    case Type.ARRAY:
                        String desc = t.getDescriptor();
                        desc = desc.substring(desc.lastIndexOf('[') + 1);
                        reportUse(Type.getType(desc), useType, siteType, siteName, siteDescriptor, sitePosition);
                        break;
                    case Type.METHOD:
                        throw new AssertionError("A method type should not enter here.");
                        //all other cases are primitive types that we don't need to consider
                    }

                } else {
                    LOG.trace("Not adding to additional classes: {}", binaryName);
                    context.additionalClassBinaryNames.remove(binaryName);
                    addUse(binaryName, useType, siteType, siteName, siteDescriptor, sitePosition);
                }
            }


            private void addUse(String binaryName, UseSite.Type useType, RawUseSite.SiteType siteType, String siteName,
                String siteDescriptor, int sitePosition) {

                Map<String, Set<RawUseSite>> useSites;

                if (visitedClassDormant) {
                    TypeElementAndUseSites tus = context.dormantTypes.get(visitedClassBinaryName);
                    if (tus == null) {
                        tus = new TypeElementAndUseSites();
                        context.dormantTypes.put(visitedClassBinaryName, tus);
                    }
                    useSites = tus.getUseSites();
                } else {
                    useSites = environment.getUseSiteMap();
                }

                Set<RawUseSite> sites = useSites.get(binaryName);
                if (sites == null) {
                    sites = new HashSet<>();
                    useSites.put(binaryName, sites);
                }

                RawUseSite useSite = new RawUseSite(useType, siteType, visitedClassBinaryName, siteName, siteDescriptor,
                    sitePosition);

                sites.add(useSite);
            }

            private boolean isTransitiveOwnerOfVisitedClass(String owningClass) {
                if (visitedClassOwners == null) {
                    return false;
                }

                if (owningClass.length() > visitedClassInternalName.length()) {
                    return false;
                }

                int startIdx = 0;
                int dollarIdx = owningClass.indexOf('$');
                int ownerIdx = 0;

                while (dollarIdx >= 0 && ownerIdx < visitedClassOwners.length) {
                    String owner = owningClass.substring(startIdx, dollarIdx);
                    if (!visitedClassOwners[ownerIdx++].equals(owner)) {
                        return false;
                    }

                    startIdx = dollarIdx + 1;
                    dollarIdx = owningClass.indexOf('$', startIdx);
                }

                if (ownerIdx < visitedClassOwners.length) {
                    return visitedClassOwners[ownerIdx].equals(owningClass.substring(startIdx));
                } else {
                    return dollarIdx == -1;
                }
            }
        }, ClassReader.SKIP_CODE);
    }

    private boolean comesFromRtJar(String typeBinaryName) {
        if (typeBinaryName.startsWith("java.")) {

            //quick check for many of the cases
            return true;
        }

        if (bootstrapClasses == null) {
            long time = 0;

            if (LOG.isTraceEnabled()) {
                LOG.trace("Building bootstrap classes cache");
                time = System.currentTimeMillis();
            }

            bootstrapClasses = new HashSet<>();
            for (File f : bootstrapClasspath) {
                ZipFile jar = null;
                try {
                    jar = new ZipFile(f);
                    Enumeration<? extends ZipEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.endsWith(".class")) {
                            name = name.substring(0, name.length() - 6).replace('/', '.');
                            bootstrapClasses.add(name);
                        }
                    }
                    jar.close();
                } catch (IOException e) {
                    LOG.error("Failed to analyze bootstrap class path entry at " + f.getAbsolutePath(), e);
                } finally {
                    try {
                        if (jar != null) {
                            jar.close();
                        }
                    } catch (IOException e) {
                        LOG.warn("Failed to close bootstrap classpath entry (" + f.getAbsolutePath() + ").", e);
                    }
                }
            }


            if (LOG.isTraceEnabled()) {
                LOG.trace("Bootstrap classpath cache built in " + (System.currentTimeMillis() - time) +
                    "ms containing " + bootstrapClasses.size() + " entries.");
            }
        }

        return bootstrapClasses.contains(typeBinaryName);
    }

    private TypeElement addConditionally(Archive currentArchive, String binaryName, String canonicalName,
        TypeElement owningType,
        boolean asError, Set<String> additionalClasses, Map<String, TypeElementAndUseSites> dormantTypes) {

        boolean add = false;

        TypeElement type = findByType(binaryName);
        if (type == null) {
            add = true;
            type = asError ? new MissingClassElement(environment, binaryName, canonicalName) :
                new TypeElement(environment, currentArchive, binaryName, canonicalName);
        }

        if (add) {
            LOG.trace("Adding to tree: {}, under superType {}", type, owningType);
            if (owningType == null) {
                environment.getTree().getRootsUnsafe().add(type);
            } else {
                owningType.getChildren().add(type);
            }

            registerType(type, additionalClasses, dormantTypes);
        }

        return type;
    }

    private void registerType(TypeElement type, Set<String> additionalClasses,
        Map<String, TypeElementAndUseSites> dormantTypes) {
        boolean asError = type instanceof MissingClassElement;

        typeCache.put(type.getBinaryName(), type);
        TypeElementAndUseSites useSites = dormantTypes.remove(type.getBinaryName());
        if (useSites != null && !useSites.getUseSites().isEmpty()) {
            for (Map.Entry<String, Set<RawUseSite>> e : useSites.getUseSites().entrySet()) {
                additionalClasses.add(e.getKey());
                addUses(e.getKey(), e.getValue());
            }
        }

        if (!asError) {
            additionalClasses.remove(type.getBinaryName());
        }

        //eagerly load the inner classes into the cache
        for (JavaElement c : type.getChildren()) {
            if (c instanceof TypeElement) {
                String binaryName = ((TypeElement) c).getBinaryName();
                typeCache.put(binaryName, (TypeElement) c);
                if (!asError) {
                    additionalClasses.remove(binaryName);
                }
            }
        }
    }

    private void addUses(String binaryName, Collection<RawUseSite> useSites) {
        Set<RawUseSite> sites = environment.getUseSiteMap().get(binaryName);
        if (sites == null) {
            sites = new HashSet<>();
            environment.getUseSiteMap().put(binaryName, sites);
        }

        sites.addAll(useSites);
    }

    private TypeElement findByType(String binaryName) {
        return typeCache.get(binaryName);
    }

    private static boolean isPublic(int access) {
        return (access & Opcodes.ACC_SYNTHETIC) == 0 && (access & Opcodes.ACC_BRIDGE) == 0 &&
            ((access & Opcodes.ACC_PUBLIC) != 0 || (access & Opcodes.ACC_PROTECTED) != 0);
    }

    private boolean removeClassesFromRtJar(Set<String> classes) {
        boolean changed = false;
        LOG.trace("Identified additional classes contributing to API not on classpath (maybe in rt.jar): {}",
            classes);
        Iterator<String> it = classes.iterator();
        while (it.hasNext()) {
            String binaryName = it.next();
            if (comesFromRtJar(binaryName)) {
                changed = true;
                it.remove();
            }
        }

        return changed;
    }


    private static class OuterNameInnerNamePair implements Comparable<OuterNameInnerNamePair> {
        final String outerName;
        final String innerName;

        private OuterNameInnerNamePair(String outerName, String innerName) {
            this.outerName = outerName;
            this.innerName = innerName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            OuterNameInnerNamePair that = (OuterNameInnerNamePair) o;

            if (outerName != null ? !outerName.equals(that.outerName) : that.outerName != null) {
                return false;
            }

            if (innerName != null ? !innerName.equals(that.innerName) : that.innerName != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = outerName != null ? outerName.hashCode() : 0;
            result = 31 * result + (innerName != null ? innerName.hashCode() : 0);
            return result;
        }

        @Override
        public int compareTo(OuterNameInnerNamePair o) {
            int ret = safeCompare(outerName, o.outerName);

            return ret != 0 ? ret : safeCompare(innerName, o.innerName);
        }

        private static int safeCompare(String a, String b) {
            int ret;
            if (a == null) {
                ret = b == null ? 0 : 1;
            } else if (b == null) {
                ret = -1;
            } else {
                ret = a.compareTo(b);
            }

            return ret;
        }
    }

    private static class TypeElementAndUseSites {
        TypeElement type;
        private Map<String, Set<RawUseSite>> useSites;

        TypeElementAndUseSites() {

        }

        TypeElementAndUseSites(TypeElement type) {
            this.type = type;
        }

        public Map<String, Set<RawUseSite>> getUseSites() {
            if (useSites == null) {
                useSites = new HashMap<>();
            }

            return useSites;
        }
    }

    private static class InitTreeContext {
        boolean onlyAddAdditional;
        final Set<String> additionalClassBinaryNames = new HashSet<>();
        final TreeMap<String, Set<TypeElement>> innerClassPositions = new TreeMap<>(
            new Comparator<String>() {
                @Override
                public int compare(String a, String b) {
                    //order the classes by the number of their owners.
                    //we're working with internal names here, so $ can both be the separator of inner class name
                    //and an ordinary part of the name but the important point here is that a more nested class
                    //should never precede the less nested class. So the $ ambiguity is OK here.

                    return countDollars(a) - countDollars(b);
                }

                private int countDollars(String s) {
                    int ret = 0;
                    int startIdx = 0;

                    do {
                        startIdx = s.indexOf('$', startIdx) + 1;
                        //this will be off by 1, but consistently so ;)
                        ret++;
                    } while (startIdx > 0);

                    return ret;
                }
            });
        final Map<String, TypeElementAndUseSites> dormantTypes = new HashMap<>();
    }
}
