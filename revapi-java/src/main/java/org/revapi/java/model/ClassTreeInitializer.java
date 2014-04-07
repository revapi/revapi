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

package org.revapi.java.model;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nullable;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.revapi.Archive;
import org.revapi.java.JavaElement;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.query.Filter;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class ClassTreeInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(ClassTreeInitializer.class);

    private final ProbingEnvironment environment;
    private final MissingClassReporting reporting;

    public ClassTreeInitializer(ProbingEnvironment environment, MissingClassReporting missingClassReporting) {
        this.environment = environment;
        this.reporting = missingClassReporting;
    }

    public void initTree() throws IOException {
        Set<String> additionalClasses = new HashSet<>();
        boolean newAdditionalClassesDetected;
        boolean secondRunOrSupplementaryArchives = false;

        do {
            Set<String> oldAdditionalClasses = new HashSet<>(additionalClasses);

            for (Archive a : environment.getApi().getArchives()) {
                LOG.trace("Processing archive {}", a.getName());
                processArchive(a, additionalClasses, secondRunOrSupplementaryArchives);
            }

            secondRunOrSupplementaryArchives = true;

            LOG.trace("Identified additional API classes to be found in classpath: {}", additionalClasses);

            if (environment.getApi().getSupplementaryArchives() != null) {
                for (Archive a : environment.getApi().getSupplementaryArchives()) {
                    LOG.trace("Processing archive {}", a.getName());
                    processArchive(a, additionalClasses, true);
                }
            }

            if (!additionalClasses.isEmpty()) {
                LOG.trace("Identified additional classes contributing to API not on classpath (maybe in rt.jar): {}",
                    additionalClasses);
                Iterator<String> it = additionalClasses.iterator();
                while (it.hasNext()) {
                    String typeDescriptor = it.next();
                    if (comesFromRtJar(typeDescriptor)) {
                        it.remove();
                    }
                }
            }

            newAdditionalClassesDetected = !oldAdditionalClasses.equals(additionalClasses);
        } while (newAdditionalClassesDetected);


        if (!additionalClasses.isEmpty()) {
            List<String> prettyNames = convertDescriptorToTypeNames(additionalClasses);

            if (reporting == null || reporting == MissingClassReporting.ERROR) {
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
                for (String desc : additionalClasses) {
                    Type t = Type.getType(desc);
                    String binary = t.getClassName();
                    addConditionally(Type.getType(desc), binary, null, true);
                }
            }
        }

        cleanUpInnerClasses(environment.getTree().getRootsUnsafe());

        if (LOG.isTraceEnabled()) {
            LOG.trace("Public API class tree in {} initialized to: {}", environment.getApi(), environment.getTree());
        }
    }

    private List<String> convertDescriptorToTypeNames(Set<String> additionalClasses) {
        List<String> ret = new ArrayList<>(additionalClasses.size());

        for (String desc : additionalClasses) {
            String typeName = Type.getType(desc).getClassName();
            ret.add(typeName);
        }

        Collections.sort(ret);

        return ret;
    }

    private void cleanUpInnerClasses(SortedSet<? extends JavaElement> nodes) {
        // we need to track the inner classes during the classes processing so that we know which ones are part of the
        // API but once we have that complete picture, we actually need to remove the inner classes from the forest again
        // because a) they are possibly misplaced by the crude algorithm we're using and b) they will be added by the
        // the initialization code in JavaElementBase along with methods and fields.

        // we only add types to the forest at this stage, so this is safe
        @SuppressWarnings("unchecked")
        Set<TypeElement> types = (Set<TypeElement>) nodes;

        Iterator<TypeElement> it = types.iterator();
        while (it.hasNext()) {
            TypeElement type = it.next();
            if (isInnerClass(type) && isEnclosingTypeInTree(type)) {
                it.remove();
            }

            cleanUpInnerClasses(type.getChildren());
        }
    }

    private boolean isInnerClass(TypeElement type) {
        String binaryName = type.getBinaryName();
        String canonicalName = type.getCanonicalName();

        // binary names can get confused by $ as part of the name of the class, not so canonical names

        // the +1 is ok - if . is not found, we have a class in default package
        int classNameStartIdx = canonicalName.lastIndexOf('.') + 1;

        if (classNameStartIdx == 0) {
            return false;
        }

        //make sure the last dot wasn't actually just separating top-level class name from a package name
        return binaryName.length() > classNameStartIdx && binaryName.charAt(classNameStartIdx - 1) == '$';
    }

    private boolean isEnclosingTypeInTree(TypeElement type) {
        String binaryName = type.getBinaryName();
        String canonicalName = type.getCanonicalName();

        int classNameStartIdx = binaryName.lastIndexOf('/') + 1;

        while (classNameStartIdx < canonicalName.length()) {
            int nextInnerClassStartIdx = canonicalName.indexOf('.', classNameStartIdx);
            if (nextInnerClassStartIdx == -1) {
                break;
            }

            final String enclosingTypeCanonicalName = canonicalName.substring(0, nextInnerClassStartIdx);
            List<TypeElement> found = environment.getTree()
                .searchUnsafe(TypeElement.class, true, new Filter<TypeElement>() {

                    @Override
                    public boolean applies(@Nullable TypeElement element) {
                        return element != null && enclosingTypeCanonicalName.equals(element.getCanonicalName());
                    }

                    @Override
                    public boolean shouldDescendInto(@Nullable Object element) {
                        return true;
                    }
                }, null);

            if (!found.isEmpty()) {
                return true;
            }

            classNameStartIdx = nextInnerClassStartIdx + 1;
        }

        return false;
    }

    private void processArchive(Archive a, Set<String> additionalClasses, boolean onlyAddAdditional)
        throws IOException {
        if (a.getName().toLowerCase().endsWith(".jar")) {
            processJarArchive(a, additionalClasses, onlyAddAdditional);
        } else if (a.getName().toLowerCase().endsWith(".class")) {
            processClassFile(a, additionalClasses, onlyAddAdditional);
        }
    }

    private void processJarArchive(Archive a, Set<String> additionalClasses,
        boolean onlyAddAdditional) throws IOException {
        try (ZipInputStream jar = new ZipInputStream(a.openStream())) {

            ZipEntry entry = jar.getNextEntry();

            while (entry != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".class")) {
                    processClassBytes(jar, additionalClasses, onlyAddAdditional);
                }

                entry = jar.getNextEntry();
            }
        }
    }

    private void processClassFile(Archive a, Set<String> additionalClasses, boolean onlyAddAdditional)
        throws IOException {
        try (InputStream data = a.openStream()) {
            processClassBytes(data, additionalClasses, onlyAddAdditional);
        }
    }

    private void processClassBytes(InputStream data, final Set<String> additionalClasses,
        final boolean onlyAddAdditional) throws IOException {
        ClassReader classReader = new ClassReader(data);

        classReader.accept(new ClassVisitor(Opcodes.ASM4) {

            private String mainName;
            private int mainAccess;
            private boolean isPublicAPI;
            private boolean processingInnerClass;
            private StringBuilder innerClassCanonicalName = null;
            private List<String> innerClassCanonicalNameParts;
            private List<String> innerClassBinaryNameParts;
            private boolean maybeInner;
            private int currentInnerClassTraversePos;

            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {

                mainName = name;
                mainAccess = access;
                isPublicAPI = isPublic(mainAccess);
                if (onlyAddAdditional && !isPublicAPI) {
                    //the class is part of the public API if some other class declared in a public capacity.
                    //if the class itself is not public then we have a check that will report such class as "suspicious".
                    //TODO implement such "private class part of public api" check - ideally we should report how we
                    //found out it is part of the public API, which is going to be hard because our checks only have
                    //access to javax.lang.model representation of the elements - no additional info can be currently
                    //supplied.
                    isPublicAPI = additionalClasses.contains(Type.getObjectType(name).getDescriptor());
                }
                maybeInner = name.indexOf('$') >= 0;

                if (isPublicAPI) {
                    addToAdditionalClasses(Type.getObjectType(superName));
                    for (String iface : interfaces) {
                        addToAdditionalClasses(Type.getObjectType(iface));
                    }
                }
                LOG.trace("visit(): name={}, signature={}, publicAPI={}, onlyAdditional={}", name, signature,
                    isPublicAPI, onlyAddAdditional);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                //only consider public or protected fields - only those contribute to the API
                if (isPublicAPI && isPublic(access)) {
                    addToAdditionalClasses(Type.getType(desc));
                }
                return null;
            }

            @Override
            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                if (isPublicAPI && maybeInner && innerName != null) {
                    boolean considerThisName = false;
                    if (outerName != null) {
                        if (innerClassCanonicalName == null) {
                            String base = Type.getObjectType(outerName).getClassName();
                            innerClassCanonicalName = new StringBuilder(base);
                            innerClassCanonicalNameParts = new ArrayList<>();
                            innerClassBinaryNameParts = new ArrayList<>();
                            innerClassCanonicalNameParts.add(base);
                            innerClassBinaryNameParts.add(outerName);

                            currentInnerClassTraversePos = outerName.length() + 1;

                            if (innerClassCanonicalName.length() < mainName.length() && containsAt(mainName, innerName,
                                currentInnerClassTraversePos)) {

                                innerClassCanonicalName.append(".").append(innerName);
                                currentInnerClassTraversePos += innerName.length() + 1;

                                innerClassCanonicalNameParts.add(innerName);
                                innerClassBinaryNameParts.add(innerName);

                                considerThisName = true;
                            }
                        } else if (innerClassCanonicalName.length() < mainName.length() &&
                            containsAt(mainName, innerName, currentInnerClassTraversePos)) {

                            innerClassCanonicalName.append(".").append(innerName);
                            currentInnerClassTraversePos += innerName.length() + 1;

                            innerClassCanonicalNameParts.add(innerName);
                            innerClassBinaryNameParts.add(innerName);

                            considerThisName = true;
                        }
                    }

                    LOG.trace("visitInnerClass(): name={}, outerName={}, innerName={}, canonical={}", name, outerName,
                        innerName, innerClassCanonicalName);

                    if (considerThisName) {
                        processingInnerClass = processingInnerClass || mainName.equals(name);
                    }
                }
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                String[] exceptions) {
                //only consider public or protected methods - only those contribute to the API
                if (isPublicAPI && isPublic(access)) {
                    addToAdditionalClasses(Type.getReturnType(desc));
                    for (Type t : Type.getArgumentTypes(desc)) {
                        addToAdditionalClasses(t);
                    }

                    if (exceptions != null && exceptions.length > 0) {
                        for (String ex : exceptions) {
                            addToAdditionalClasses(Type.getObjectType(ex));
                        }
                    }
                }

                return null;
            }

            @Override
            public void visitEnd() {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Visited {}, isAPI={}, isInner={}, onlyAdditional={}, innerCanonical={}", mainName,
                        isPublicAPI,
                        processingInnerClass, onlyAddAdditional, innerClassCanonicalName);
                }
                if (isPublicAPI) {
                    Type t = Type.getObjectType(mainName);
                    if (!onlyAddAdditional || additionalClasses.contains(t.getDescriptor())) {
                        if (onlyAddAdditional) {
                            LOG.trace("Found in additional API classes");
                        }

                        if (innerClassCanonicalName == null) {
                            addConditionally(t, Type.getObjectType(mainName).getClassName(), null, false);
                        } else if (processingInnerClass) {
                            if (!onlyAddAdditional) {
                                StringBuilder binaryName = new StringBuilder();
                                StringBuilder canonicalName = new StringBuilder();
                                TypeElement superType = null;

                                for (int i = 0; i < innerClassBinaryNameParts.size(); ++i) {
                                    binaryName.append(innerClassBinaryNameParts.get(i));
                                    canonicalName.append(innerClassCanonicalNameParts.get(i));

                                    superType = addConditionally(Type.getObjectType(binaryName.toString()),
                                        canonicalName.toString(), superType, false);

                                    binaryName.append("$");
                                    canonicalName.append(".");
                                }
                            } else {
                                //we're adding the additional classes here, so only add the ones we actually need
                                //under parents we "see".u
                                StringBuilder binaryName = new StringBuilder();
                                StringBuilder canonicalName = new StringBuilder();
                                TypeElement superType = null;

                                for (int i = 0; i < innerClassBinaryNameParts.size(); ++i) {
                                    binaryName.append(innerClassBinaryNameParts.get(i));
                                    canonicalName.append(innerClassCanonicalNameParts.get(i));

                                    superType = findByType(Type.getObjectType(binaryName.toString()), superType);

                                    binaryName.append("$");
                                    canonicalName.append(".");
                                }

                                binaryName.replace(binaryName.length() - 1, binaryName.length(), "");
                                canonicalName.replace(canonicalName.length() - 1, canonicalName.length(), "");
                                addConditionally(Type.getObjectType(binaryName.toString()), canonicalName.toString(),
                                    superType, false);
                            }
                        }
                    }

                    LOG.trace("Removing from additional classes: {}", t.getDescriptor());
                    additionalClasses.remove(t.getDescriptor());
                }
            }

            private void addToAdditionalClasses(Type t) {
                if (t.getSort() < Type.ARRAY) {
                    //primitive type
                    return;
                }

                if (findByType(t, null) == null) {

                    switch (t.getSort()) {
                    case Type.OBJECT:
                        additionalClasses.add(t.getDescriptor());
                        LOG.trace("Adding to additional classes: {}", t.getDescriptor());
                        break;
                    case Type.ARRAY:
                        String desc = t.getDescriptor();
                        desc = desc.substring(desc.lastIndexOf('[') + 1);
                        addToAdditionalClasses(Type.getType(desc));
                        break;
                    case Type.METHOD:
                        throw new AssertionError("A method type should not enter here.");
                        //all other cases are primitive types that we don't need to consider
                    }

                } else {
                    LOG.trace("Not adding to additional classes: {}", t.getDescriptor());
                    additionalClasses.remove(t.getDescriptor());
                }
            }

            private boolean containsAt(String string, String substring, int startIdx) {
                int stringIdx = startIdx;
                int stringLen = string.length();
                int substringIdx = 0;
                int substringLen = substring.length();

                boolean cont = stringIdx < stringLen && substringIdx < substringLen;
                while (cont) {
                    if (string.charAt(stringIdx) != substring.charAt(substringIdx)) {
                        return false;
                    }

                    stringIdx++;
                    substringIdx++;

                    cont = stringIdx < stringLen && substringIdx < substringLen;
                }

                return stringIdx == stringLen || string.charAt(stringIdx) == '$';
            }
        }, ClassReader.SKIP_CODE);
    }

    private boolean comesFromRtJar(String typeDescriptor) {
        Type t = Type.getType(typeDescriptor);
        String className = t.getClassName();

        try {
            Class<?> cls = Class.forName(className);
            ClassLoader cl = cls.getClassLoader();

            if (cl == null) {
                return true;
            }

            String classFile = t.getInternalName() + ".class";
            URL classURL = cl.getResource(classFile);
            if (classURL == null) {
                //??? this is strange, but I guess anything can happen with classloaders
                return false;
            }

            //TODO should we make the location of rt.jar configurable? or do we just assume that java classes
            //are always backwards compatible and therefore there's no need to do that?
            String rtJarPath = System.getProperty("java.home") + "/lib/rt.jar";
            if ('/' != File.separatorChar) {
                rtJarPath = rtJarPath.replace(File.separatorChar, '/');
            }

            return classURL.toString().contains(rtJarPath);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private TypeElement addConditionally(Type t, String canonicalName, TypeElement superType, boolean asError) {
        boolean add = false;

        TypeElement type = findByType(t, superType);
        if (type == null) {
            String binaryName = t.getClassName();
            add = true;
            type = asError ? new MissingClassElement(environment, binaryName, canonicalName) :
                new TypeElement(environment, binaryName, canonicalName);
        }

        if (add) {
            LOG.trace("Adding to tree: {}, under superType {}", type, superType);
            if (superType == null) {
                environment.getTree().getRootsUnsafe().add(type);
                if (!asError) {
                    environment.getAllApiClasses().add(canonicalName);
                }
            } else {
                superType.getChildren().add(type);
            }
        }

        return type;
    }

    private TypeElement findByType(final Type t, TypeElement superType) {
        List<TypeElement> found = environment.getTree().searchUnsafe(TypeElement.class, true,
            new Filter<TypeElement>() {
                @Override
                public boolean applies(@Nullable TypeElement object) {
                    return object != null && t.getClassName().equals(object.getBinaryName());
                }

                @Override
                public boolean shouldDescendInto(@Nullable Object object) {
                    return true;
                }
            }, superType
        );

        return found.isEmpty() ? null : found.get(0);
    }

    public static boolean isAccessible(Element element) {
        return element.getModifiers().contains(Modifier.PUBLIC) || element.getModifiers().contains(Modifier.PROTECTED);
    }

    private static boolean isPublic(int access) {
        return (access & Opcodes.ACC_PUBLIC) != 0 || (access & Opcodes.ACC_PROTECTED) != 0;
    }
}
