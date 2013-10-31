/*
 * Copyright 2013 Lukas Krejci
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

package org.revapi.java;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import org.revapi.Archive;
import org.revapi.Element;
import org.revapi.java.elements.Access;
import org.revapi.java.elements.AnnotationAttributeElement;
import org.revapi.java.elements.AnnotationElement;
import org.revapi.java.elements.ArrayAnnotationValueElement;
import org.revapi.java.elements.ClassAnnotationValueElement;
import org.revapi.java.elements.ClassElement;
import org.revapi.java.elements.ClassTree;
import org.revapi.java.elements.EnumAnnotationValueElement;
import org.revapi.java.elements.FieldElement;
import org.revapi.java.elements.MethodElement;
import org.revapi.java.elements.MethodParameterElement;
import org.revapi.java.elements.PrimitiveAnnotationValueElement;
import org.revapi.simple.SimpleArchiveAnalyzer;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public class ClassFileAnalyzer extends SimpleArchiveAnalyzer<Java, ClassTree> {

    private static Access getAccessFromOpcodes(int opcodes) {
        if ((opcodes & Opcodes.ACC_PUBLIC) != 0) {
            return Access.PUBLIC;
        } else if ((opcodes & Opcodes.ACC_PROTECTED) != 0) {
            return Access.PROTECTED;
        } else if ((opcodes & Opcodes.ACC_PRIVATE) != 0) {
            return Access.PRIVATE;
        } else {
            return Access.PACKAGE_PRIVATE;
        }
    }

    private static EnumSet<ClassElement.Modifier> getClassModifiersFromOpcodes(int opcodes) {
        EnumSet<ClassElement.Modifier> ret = EnumSet.noneOf(ClassElement.Modifier.class);

        if ((opcodes & Opcodes.ACC_FINAL) != 0) {
            ret.add(ClassElement.Modifier.FINAL);
        }

        if ((opcodes & Opcodes.ACC_INTERFACE) != 0) {
            ret.add(ClassElement.Modifier.INTERFACE);
        }

        if ((opcodes & Opcodes.ACC_ABSTRACT) != 0) {
            ret.add(ClassElement.Modifier.ABSTRACT);
        }

        if ((opcodes & Opcodes.ACC_SYNTHETIC) != 0) {
            ret.add(ClassElement.Modifier.SYNTHETIC);
        }

        if ((opcodes & Opcodes.ACC_ANNOTATION) != 0) {
            ret.add(ClassElement.Modifier.ANNOTATION);
        }

        if ((opcodes & Opcodes.ACC_ENUM) != 0) {
            ret.add(ClassElement.Modifier.ENUM);
        }

        if ((opcodes & Opcodes.ACC_DEPRECATED) != 0) {
            ret.add(ClassElement.Modifier.DEPRECATED);
        }

        return ret;
    }

    public static EnumSet<FieldElement.Modifier> getFieldModifiersFromOpcodes(int opcodes) {
        EnumSet<FieldElement.Modifier> ret = EnumSet.noneOf(FieldElement.Modifier.class);

        if ((opcodes & Opcodes.ACC_STATIC) != 0) {
            ret.add(FieldElement.Modifier.STATIC);
        }

        if ((opcodes & Opcodes.ACC_FINAL) != 0) {
            ret.add(FieldElement.Modifier.FINAL);
        }

        if ((opcodes & Opcodes.ACC_VOLATILE) != 0) {
            ret.add(FieldElement.Modifier.VOLATILE);
        }

        if ((opcodes & Opcodes.ACC_TRANSIENT) != 0) {
            ret.add(FieldElement.Modifier.TRANSIENT);
        }

        if ((opcodes & Opcodes.ACC_SYNTHETIC) != 0) {
            ret.add(FieldElement.Modifier.SYNTHETIC);
        }

        if ((opcodes & Opcodes.ACC_DEPRECATED) != 0) {
            ret.add(FieldElement.Modifier.DEPRECATED);
        }

        return ret;
    }

    public static EnumSet<MethodElement.Modifier> getMethodModifiersFromOpcodes(int opcodes) {
        EnumSet<MethodElement.Modifier> ret = EnumSet.noneOf(MethodElement.Modifier.class);

        if ((opcodes & Opcodes.ACC_STATIC) != 0) {
            ret.add(MethodElement.Modifier.STATIC);
        }

        if ((opcodes & Opcodes.ACC_FINAL) != 0) {
            ret.add(MethodElement.Modifier.FINAL);
        }

        if ((opcodes & Opcodes.ACC_SYNCHRONIZED) != 0) {
            ret.add(MethodElement.Modifier.SYNCHRONIZED);
        }

        if ((opcodes & Opcodes.ACC_BRIDGE) != 0) {
            ret.add(MethodElement.Modifier.BRIDGE);
        }

        if ((opcodes & Opcodes.ACC_NATIVE) != 0) {
            ret.add(MethodElement.Modifier.NATIVE);
        }

        if ((opcodes & Opcodes.ACC_ABSTRACT) != 0) {
            ret.add(MethodElement.Modifier.ABSTRACT);
        }

        if ((opcodes & Opcodes.ACC_STRICT) != 0) {
            ret.add(MethodElement.Modifier.STRICT);
        }

        if ((opcodes & Opcodes.ACC_SYNTHETIC) != 0) {
            ret.add(MethodElement.Modifier.SYNTHETIC);
        }

        return ret;
    }

    private static class AnnotationParsingVisitor extends AnnotationVisitor {
        final List<Element> parsed;
        final Element parent;
        final boolean parentIsArray;

        AnnotationParsingVisitor(Element parent, boolean parentIsArray) {
            super(Opcodes.ASM4);
            this.parent = parent;
            this.parentIsArray = parentIsArray;
            this.parsed = new ArrayList<>();
        }

        @Override
        public void visit(String name, Object value) {
            if (value instanceof Type) {
                String className = ((Type) value).getClassName();
                addAttributeValue(name, new ClassAnnotationValueElement(((Type) value).getDescriptor(), className));
            } else if (value.getClass().isArray()) {
                //we only receive primitive arrays here...
                ArrayAnnotationValueElement array = new ArrayAnnotationValueElement();
                addAttributeValue(name, array);

                int len = Array.getLength(value);
                for (int i = 0; i < len; ++i) {
                    Object v = Array.get(value, i);
                    array.getChildren().add(
                        new PrimitiveAnnotationValueElement(Type.getType(v.getClass()).getDescriptor(), v));
                }
            } else {
                addAttributeValue(name, new PrimitiveAnnotationValueElement(Type.getType(value.getClass())
                    .getDescriptor(), value));
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String desc) {
            AnnotationElement annotation = new AnnotationElement(Type.getType(desc).getClassName(), desc);
            addAttributeValue(name, annotation);

            return new AnnotationParsingVisitor(annotation, false);
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            ArrayAnnotationValueElement array = new ArrayAnnotationValueElement();
            addAttributeValue(name, array);

            return new AnnotationParsingVisitor(array, true);
        }

        @Override
        public void visitEnd() {
            if (parsed != null) {
                parent.getChildren().addAll(parsed);
            }
        }

        @Override
        public void visitEnum(String name, String desc, String value) {
            EnumAnnotationValueElement eae = new EnumAnnotationValueElement(desc, Type.getType(desc).getClassName(),
                value);
            addAttributeValue(name, eae);
        }

        private void addAttributeValue(String attributeName, Element value) {
            if (parentIsArray) {
                parsed.add(value);
            } else {
                AnnotationAttributeElement attr = new AnnotationAttributeElement(attributeName);
                attr.getChildren().add(value);

                parsed.add(attr);
            }
        }
    }

    public ClassFileAnalyzer(Archive archive) {
        super(archive);
    }

    @Override
    protected ClassTree doAnalyze() throws Exception {
        ClassReader classReader = new ClassReader(openStream());

        final ClassTree ret = new ClassTree();

        classReader.accept(new ClassVisitor(Opcodes.ASM4) {
            ClassElement classElement;

            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {

                classElement = new ClassElement(Type.getObjectType(name).getClassName(), name, signature,
                    getAccessFromOpcodes(access));

                classElement.setClassVersion(version);
                if (interfaces != null) {
                    String[] ifaces = new String[interfaces.length];
                    for (int i = 0; i < interfaces.length; ++i) {
                        ifaces[i] = Type.getObjectType(interfaces[i]).getClassName();
                    }
                    classElement.setInterfaces(ifaces);
                }
                classElement.setModifiers(getClassModifiersFromOpcodes(access));
                classElement.setSuperClass(Type.getObjectType(superName).getClassName());
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                AnnotationElement annotation = new AnnotationElement(Type.getType(desc).getClassName(), desc);
                classElement.getChildren().add(annotation);
                return new AnnotationParsingVisitor(annotation, false);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                final FieldElement field = new FieldElement(Type.getType(desc).getClassName(), name, desc, signature,
                    getAccessFromOpcodes(access));

                if (value instanceof String) {
                    field.setConstantStringValue((String) value);
                } else if (value instanceof Number) {
                    field.setConstantNumericValue((Number) value);
                }

                field.setModifiers(getFieldModifiersFromOpcodes(access));

                classElement.getChildren().add(field);

                return new FieldVisitor(Opcodes.ASM4) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        AnnotationElement annotation = new AnnotationElement(Type.getType(desc).getClassName(), desc);
                        field.getChildren().add(annotation);
                        return new AnnotationParsingVisitor(annotation, false);
                    }
                };
            }

            @Override
            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                //TODO implement
                super.visitInnerClass(name, outerName, innerName, access);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                String[] exceptions) {

                final MethodElement methodElement = new MethodElement(Type.getReturnType(desc).getClassName(), name,
                    desc, signature,
                    getAccessFromOpcodes(
                        access));

                methodElement.setModifiers(getMethodModifiersFromOpcodes(access));

                if (exceptions != null) {
                    String[] exceptionTypes = new String[exceptions.length];
                    for (int i = 0; i < exceptions.length; ++i) {
                        exceptionTypes[i] = Type.getObjectType(exceptions[i]).getClassName();
                    }

                    methodElement.setExceptionTypes(exceptionTypes);
                }

                Type[] args = Type.getArgumentTypes(desc);
                if (args != null && args.length > 0) {
                    int i = 0;
                    for (Type arg : args) {
                        //TODO null as signature here is OK?
                        MethodParameterElement par = new MethodParameterElement(i++, arg.getClassName(),
                            arg.getDescriptor(), null);
                        methodElement.getChildren().add(par);
                    }
                }

                classElement.getChildren().add(methodElement);

                return new MethodVisitor(Opcodes.ASM4) {
                    @Override
                    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
                        MethodParameterElement par = methodElement
                            .searchChildren(MethodParameterElement.class, false, null)
                            .get(parameter);

                        AnnotationElement annotation = new AnnotationElement(Type.getType(desc).getClassName(), desc);
                        par.getChildren().add(annotation);

                        return new AnnotationParsingVisitor(annotation, false);
                    }

                    @Override
                    public AnnotationVisitor visitAnnotationDefault() {
                        return new AnnotationParsingVisitor(methodElement, false);
                    }

                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        AnnotationElement annotation = new AnnotationElement(Type.getType(desc).getClassName(), desc);
                        methodElement.getChildren().add(annotation);
                        return new AnnotationParsingVisitor(annotation, false);
                    }
                };
            }

            @Override
            public void visitOuterClass(String owner, String name, String desc) {
                //TODO implement
                super.visitOuterClass(owner, name, desc);
            }

            @Override
            public void visitEnd() {
                ret.getRoots().add(classElement);
            }
        }, ClassReader.SKIP_CODE);

        return ret;
    }
}
