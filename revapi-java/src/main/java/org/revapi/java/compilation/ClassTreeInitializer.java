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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nullable;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.revapi.Archive;
import org.revapi.java.AnalysisConfiguration;
import org.revapi.java.model.MissingClassElement;
import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.UseSite;
import org.revapi.query.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final boolean ignoreAdditionalArchivesContributions;

    public ClassTreeInitializer(ProbingEnvironment environment,
        AnalysisConfiguration.MissingClassReporting missingClassReporting, boolean ignoreMissingAnnotations,
        Set<File> bootstrapClasspath, boolean ignoreAdditionalArchivesContributions) {
        this.environment = environment;
        this.reporting = missingClassReporting;
        this.ignoreMissingAnnotations = ignoreMissingAnnotations;
        this.bootstrapClasspath = bootstrapClasspath;
        this.ignoreAdditionalArchivesContributions = ignoreAdditionalArchivesContributions;
    }

    public void initTree() throws IOException {
        InitTreeContext context = new InitTreeContext(new TypeTreeConstructor(environment, bootstrapClasspath));

        long time = System.currentTimeMillis();

        for (Archive a : environment.getApi().getArchives()) {
            LOG.trace("Processing archive {}", a.getName());
            processArchive(a, context);
        }

        if (context.typeTreeConstructor.hasUnknownClasses() && environment.getApi().getSupplementaryArchives() != null) {
            context.processingSupplementaryArchives = true;

            for (Archive a : environment.getApi().getSupplementaryArchives()) {
                LOG.trace("Processing archive {}", a.getName());
                processArchive(a, context);

                // check for additional class changes inside this loop so that we exit as soon as possible if we
                // clear out the additional classes early.
                if (!context.typeTreeConstructor.hasUnknownClasses()) {
                    break;
                }
            }
        }

        TypeTreeConstructor.Results results = context.typeTreeConstructor.construct();

        if (!results.getUnknownTypeBinaryNames().isEmpty()) {
            List<String> prettyNames = new ArrayList<>(results.getUnknownTypeBinaryNames());
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
                for (String binary : results.getUnknownTypeBinaryNames()) {
                    TypeElement t = new MissingClassElement(environment, binary, binary);
                    environment.getTree().getRootsUnsafe().add(t);
                }
            }
        }

        if (LOG.isTraceEnabled()) {
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
            LOG.trace("Tree init took " + time + "ms. The resulting tree has " + num[0] + " elements.");
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

            private String visitedClassInternalName;
            private String visitedClassBinaryName;
            private String[] visitedClassOwners;
            private boolean isInnerClass;
            private int visitedInnerClassAccess;

            private TypeTreeConstructor.ClassProcessor classProcessor;

            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {

                visitedClassInternalName = name;
                visitedClassBinaryName = Type.getObjectType(name).getClassName();

                boolean visible = isAccessible(access);
                boolean isPublicAPI = !context.processingSupplementaryArchives && visible;

                classProcessor = context.typeTreeConstructor
                    .createApiClassProcessor(currentArchive, visitedClassBinaryName, isPublicAPI);

                //add the superclass and interface use sites
                reportUse(Type.getObjectType(superName), UseSite.Type.IS_INHERITED, RawUseSite.SiteType.CLASS, null,
                    null, -1);

                for (String iface : interfaces) {
                    reportUse(Type.getObjectType(iface), UseSite.Type.IS_IMPLEMENTED, RawUseSite.SiteType.CLASS,
                        null, null, -1);
                }

                if (name.indexOf('$') >= 0) {
                    visitedClassOwners = name.split("\\$");
                }

                if (LOG.isTraceEnabled()) {
                    LOG.trace("visit(): name={}, signature={}, access=0x{}", name, signature,
                        Integer.toHexString(access));
                }
            }

            @Override
            public FieldVisitor visitField(int access, final String name, final String desc, String signature,
                Object value) {
                //only consider public or protected fields - only those contribute to the API
                if (isAccessible(access)) {
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
                    //visiting some outer class of the currently processed class
                    classProcessor.getInnerClassHierarchyConstructor().addName(outerName, innerName);
                }
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                reportUse(Type.getType(desc), UseSite.Type.ANNOTATES, RawUseSite.SiteType.CLASS, null, null, -1);

                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, final String name, final String desc, String signature,
                String[] exceptions) {

                LOG.trace("Visiting method {} {}", name, desc);

                //only consider public or protected methods - only those contribute to the API
                if (isAccessible(access)) {

                    Type[] argumentTypes = Type.getArgumentTypes(desc);

                    reportUse(Type.getReturnType(desc), UseSite.Type.RETURN_TYPE, RawUseSite.SiteType.METHOD, name,
                        desc, -1);

                    //instance inner classes synthesize their constructors to always have the enclosing type as the
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
                    LOG.trace("Visited {}, isInner={}, onlyAdditional={}",
                        visitedClassInternalName, isInnerClass, context.processingSupplementaryArchives);
                }

                classProcessor.commitClass();
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
                RawUseSite useSite = new RawUseSite(useType, siteType, visitedClassBinaryName, siteName, siteDescriptor,
                    sitePosition);

                switch (t.getSort()) {
                case Type.OBJECT:
                    if (!ignoreAdditionalArchivesContributions || !context.processingSupplementaryArchives) {
                        classProcessor.addUse(binaryName, useSite);
                    }
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

    private static boolean isAccessible (int access) {
        return (access & Opcodes.ACC_SYNTHETIC) == 0 && (access & Opcodes.ACC_BRIDGE) == 0 &&
            ((access & Opcodes.ACC_PUBLIC) != 0 || (access & Opcodes.ACC_PROTECTED) != 0);
    }

    private static class InitTreeContext {
        boolean processingSupplementaryArchives;
        final TypeTreeConstructor typeTreeConstructor;

        private InitTreeContext(TypeTreeConstructor typeTreeConstructor) {
            this.typeTreeConstructor = typeTreeConstructor;
        }
    }
}
