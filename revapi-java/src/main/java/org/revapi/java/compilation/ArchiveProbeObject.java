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
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.SimpleJavaFileObject;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import org.revapi.Archive;
import org.revapi.java.model.TypeElement;
import org.revapi.query.Filter;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class ArchiveProbeObject extends SimpleJavaFileObject {
    public static final String CLASS_NAME = "Probe";

    private static final Map<Integer, Modifier> accessOpCodeToModifier = new HashMap<>();

    static {
        accessOpCodeToModifier.put(Opcodes.ACC_ABSTRACT, Modifier.ABSTRACT);
        accessOpCodeToModifier.put(Opcodes.ACC_FINAL, Modifier.FINAL);
        accessOpCodeToModifier.put(Opcodes.ACC_NATIVE, Modifier.NATIVE);
        accessOpCodeToModifier.put(Opcodes.ACC_PRIVATE, Modifier.PRIVATE);
        accessOpCodeToModifier.put(Opcodes.ACC_PROTECTED, Modifier.PROTECTED);
        accessOpCodeToModifier.put(Opcodes.ACC_PUBLIC, Modifier.PUBLIC);
        accessOpCodeToModifier.put(Opcodes.ACC_STATIC, Modifier.STATIC);
        accessOpCodeToModifier.put(Opcodes.ACC_STRICT, Modifier.STRICTFP);
        accessOpCodeToModifier.put(Opcodes.ACC_SYNCHRONIZED, Modifier.SYNCHRONIZED);
        accessOpCodeToModifier.put(Opcodes.ACC_TRANSIENT, Modifier.TRANSIENT);
        accessOpCodeToModifier.put(Opcodes.ACC_VOLATILE, Modifier.VOLATILE);
    }

    private final Iterable<Archive> archives;
    private final ProbingEnvironment environment;
    private String source;
    private int fieldCounter;


    public ArchiveProbeObject(Iterable<Archive> archives, ProbingEnvironment environment) {
        super(getSourceFileName(), Kind.SOURCE);
        this.archives = archives;
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

    private static EnumSet<Modifier> getModifiersFromAccess(int access) {
        EnumSet<Modifier> ret = EnumSet.noneOf(Modifier.class);

        for (Map.Entry<Integer, Modifier> entry : accessOpCodeToModifier.entrySet()) {
            if ((access & entry.getKey()) != 0) {
                ret.add(entry.getValue());
            }
        }

        return ret;
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

        StringBuilder bld = new StringBuilder("@" + MarkerAnnotationObject.CLASS_NAME + "\npublic class ")
            .append(CLASS_NAME).append(" {\n");

        for (Archive a : archives) {
            processArchive(a, bld);
        }
        bld.append("\n}");

        source = bld.toString();
    }

    private void processArchive(Archive a, StringBuilder bld) throws IOException {
        if (a.getName().toLowerCase().endsWith(".jar")) {
            processJarArchive(a, bld);
        } else if (a.getName().toLowerCase().endsWith(".class")) {
            processClassFile(a, bld);
        }
    }

    private void processJarArchive(Archive a, StringBuilder bld) throws IOException {
        try (ZipInputStream jar = new ZipInputStream(a.openStream())) {

            ZipEntry entry = jar.getNextEntry();

            while (entry != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".class")) {
                    processClassBytes(jar, bld);
                }

                entry = jar.getNextEntry();
            }
        }
    }

    private void processClassFile(Archive a, StringBuilder bld) throws IOException {
        try (InputStream data = a.openStream()) {
            processClassBytes(data, bld);
        }
    }

    private void processClassBytes(InputStream data, final StringBuilder bld) throws IOException {
        ClassReader classReader = new ClassReader(data);
        classReader.accept(new ClassVisitor(Opcodes.ASM4) {

            private String mainName;
            private int mainAccess;
            private boolean isInner;

            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {

                mainName = name;
                mainAccess = access;
            }

            @Override
            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                if (name.equals(mainName)) {
                    isInner = true;
                }
            }

            @Override
            public void visitEnd() {
                if (!isInner) {
                    String className = Type.getObjectType(mainName).getClassName();
                    addConditionally(className, mainAccess);
                }
            }

            private void addConditionally(String className, int access) {
                boolean add = false;
                TypeElement type = findByClassName(className);
                if (type == null) {
                    add = true;
                    type = new TypeElement(environment, className);
                }

                if (add) {
                    //only process the public classes - we can't reference non-public classes in our generated probe class
                    if ((access & Opcodes.ACC_PUBLIC) != 0) {
                        bld.append(className).append(" f").append(fieldCounter++).append(";\n");
                    }

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

        }, ClassReader.SKIP_CODE);
    }
}
