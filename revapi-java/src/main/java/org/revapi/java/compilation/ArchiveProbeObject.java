/*
 * Copyright 2014 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi.java.compilation;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.lang.model.element.NestingKind;
import javax.tools.SimpleJavaFileObject;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import org.revapi.Archive;
import org.revapi.java.model.TypeElement;
import org.revapi.query.Filter;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class ArchiveProbeObject extends SimpleJavaFileObject {
    public static final String CLASS_NAME = "Probe";

    private final Iterable<? extends Archive> archives;
    private final Iterable<? extends Archive> supplementaryArchives;
    private final ProbingEnvironment environment;
    private String source;

    public ArchiveProbeObject(Iterable<? extends Archive> archives, Iterable<? extends Archive> supplementaryArchives,
        ProbingEnvironment environment) {

        super(getSourceFileName(), Kind.SOURCE);
        this.archives = archives;
        this.supplementaryArchives = supplementaryArchives;
        this.environment = environment;
    }

    private static URI getSourceFileName() {
        try {
            return new URI(CLASS_NAME + ".java");
        } catch (URISyntaxException e) {
            //doesn't happen
            return null;
        }
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        generateIfNeeded();
        return source;
    }

    @Override
    public NestingKind getNestingKind() {
        return NestingKind.TOP_LEVEL;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        generateIfNeeded();
        return new ByteArrayInputStream(source.getBytes());
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        generateIfNeeded();
        return new StringReader(source);
    }

    private void generateIfNeeded() throws IOException {
        if (source != null) {
            return;
        }

        //notice that we don't actually need to generate any complicated code. Having the classes on the classpath
        //is enough for them to be present in the model captured during the annotation processing.
        //what we have to do though is to go through all the archives and pick the classes that we will then want to
        //check. There is no way of finding "all classes" using the javax.lang.model APIs, so we need to find them
        //upfront.
        source = "@" + MarkerAnnotationObject.CLASS_NAME + "\npublic class " + CLASS_NAME + "\n{}\n";

        Set<String> additionalClasses = new HashSet<>();

        for (Archive a : archives) {
            processArchive(a, additionalClasses, false);
        }

        if (supplementaryArchives != null) {
            for (Archive a : supplementaryArchives) {
                processArchive(a, additionalClasses, true);
            }
        }

        if (!additionalClasses.isEmpty()) {
            Iterator<String> it = additionalClasses.iterator();
            while (it.hasNext()) {
                String typeDescriptor = it.next();
                if (comesFromRtJar(typeDescriptor)) {
                    addConditionally(Type.getType(typeDescriptor));
                    it.remove();
                }
            }

            if (!additionalClasses.isEmpty()) {
                throw new IllegalStateException(
                    "The following classes that contribute to the public API could not be located: " +
                        additionalClasses);
            }
        }
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
            private boolean isInner;
            private boolean isPublicAPI;

            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {

                mainName = name;
                mainAccess = access;
                isPublicAPI = (mainAccess & Opcodes.ACC_PUBLIC) != 0 || (mainAccess & Opcodes.ACC_PROTECTED) != 0;
            }

            @Override
            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                if (name.equals(mainName)) {
                    isInner = true;
                }
            }

            @Override
            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                //only consider public or protected fields - only those contribute to the API
                if ((access & Opcodes.ACC_PUBLIC) != 0 || (access & Opcodes.ACC_PROTECTED) != 0) {
                    addToAdditionalClasses(Type.getType(desc));
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                String[] exceptions) {
                //only consider public or protected methods - only those contribute to the API
                if ((access & Opcodes.ACC_PUBLIC) != 0 || (access & Opcodes.ACC_PROTECTED) != 0) {
                    addToAdditionalClasses(Type.getReturnType(desc));
                    for (Type t : Type.getArgumentTypes(desc)) {
                        addToAdditionalClasses(t);
                    }
                }

                return null;
            }

            @Override
            public void visitEnd() {
                Type t = Type.getObjectType(mainName);
                if (!isInner && isPublicAPI) {
                    if (!onlyAddAdditional || additionalClasses.contains(t.getDescriptor())) {
                        addConditionally(t);
                    }
                }
                additionalClasses.remove(t.getDescriptor());
            }

            private void addToAdditionalClasses(Type t) {
                if (!isPublicAPI) {
                    //don't bother adding anything if we don't contribute to public API with the current class
                    return;
                }

                if (findByClassName(t.getClassName()) == null) {

                    switch (t.getSort()) {
                    case Type.OBJECT:
                        additionalClasses.add(t.getDescriptor());
                        break;
                    case Type.ARRAY:
                        String desc = t.getDescriptor();
                        desc = desc.substring(desc.lastIndexOf('[') + 1);
                        additionalClasses.add(desc);
                        break;
                    case Type.METHOD:
                        throw new AssertionError("A method type should not enter here.");
                        //all other cases are primitive types that we don't need to consider
                    }

                } else {
                    additionalClasses.remove(t.getDescriptor());
                }
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
            rtJarPath = rtJarPath.replace(File.separatorChar, '/');

            return classURL.toString().contains(rtJarPath);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void addConditionally(Type t) {
        boolean add = false;
        String className = t.getClassName();

        TypeElement type = findByClassName(className);
        if (type == null) {
            add = true;
            type = new TypeElement(environment, className);
        }

        if (add) {
            environment.getTree().getRootsUnsafe().add(type);
        }
    }

    private TypeElement findByClassName(final String className) {
        List<TypeElement> found = environment.getTree().search(TypeElement.class, true,
            new Filter<TypeElement>() {
                @Override
                public boolean applies(TypeElement object) {
                    return className.equals(object.getExplicitClassName());
                }

                @Override
                public boolean shouldDescendInto(Object object) {
                    return false;
                }
            }, null);

        return found.isEmpty() ? null : found.get(0);
    }
}
