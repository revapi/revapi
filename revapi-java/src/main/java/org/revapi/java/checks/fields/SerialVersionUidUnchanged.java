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

package org.revapi.java.checks.fields;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import org.revapi.MatchReport;
import org.revapi.java.TypeEnvironment;
import org.revapi.java.Util;
import org.revapi.java.checks.AbstractJavaCheck;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class SerialVersionUidUnchanged extends AbstractJavaCheck {

    private static final String SERIAL_VERSION_UID_FIELD_NAME = "serialVersionUID";

    @Override
    protected void doVisitField(VariableElement oldField, VariableElement newField) {
        if (oldField == null || newField == null) {
            return;
        }

        if (!SERIAL_VERSION_UID_FIELD_NAME.equals(oldField.getSimpleName().toString())) {
            return;
        }

        if (!SERIAL_VERSION_UID_FIELD_NAME.equals(newField.getSimpleName().toString())) {
            return;
        }

        PrimitiveType oldLong = getOldTypeEnvironment().getTypeUtils().getPrimitiveType(TypeKind.LONG);
        if (!getOldTypeEnvironment().getTypeUtils().isSameType(oldField.asType(), oldLong)) {
            return;
        }

        PrimitiveType newLong = getNewTypeEnvironment().getTypeUtils().getPrimitiveType(TypeKind.LONG);
        if (!getNewTypeEnvironment().getTypeUtils().isSameType(newField.asType(), newLong)) {
            return;
        }

        if (!oldField.getModifiers().contains(Modifier.STATIC) || !oldField.getModifiers().contains(Modifier.FINAL)) {
            return;
        }

        if (!newField.getModifiers().contains(Modifier.STATIC) || !newField.getModifiers().contains(Modifier.FINAL)) {
            return;
        }

        TypeElement oldType = (TypeElement) oldField.getEnclosingElement();
        TypeElement newType = (TypeElement) newField.getEnclosingElement();

        long computedOldSUID = computeSerialVersionUID(oldType, getOldTypeEnvironment());
        long computedNewSUID = computeSerialVersionUID(newType, getNewTypeEnvironment());
        Long actualOldSUID = (Long) oldField.getConstantValue();
        Long actualNewSUID = (Long) newField.getConstantValue();

        if (Objects.equals(actualOldSUID, actualNewSUID) && computedOldSUID != computedNewSUID) {
            pushActive(oldField, newField);
        }
    }

    @Override
    protected List<MatchReport.Problem> doEnd() {
        //TODO implement
        return super.doEnd();
    }

    /**
     * Adapted from {@link java.io.ObjectStreamClass#computeDefaultSUID(java.lang.Class)} method.
     */
    public static long computeSerialVersionUID(TypeElement type, TypeEnvironment environment) {

        TypeElement javaIoSerializable = environment.getElementUtils().getTypeElement("java.io.Serializable");

        if (!environment.getTypeUtils().isAssignable(type.asType(), javaIoSerializable.asType())) {
            return 0L;
        }
//        if (!Serializable.class.isAssignableFrom(cl) || Proxy.isProxyClass(cl))
//        {
//            return 0L;
//        }

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(bout);

            dout.writeUTF(type.getQualifiedName().toString());

            int classMods = asReflectiveModifiers(type, Modifier.PUBLIC, Modifier.FINAL, Modifier.ABSTRACT);
            if (type.getKind() == ElementKind.INTERFACE) {
                classMods |= java.lang.reflect.Modifier.INTERFACE;
            }
//            int classMods = cl.getModifiers() &
//                (java.lang.reflect.Modifier.PUBLIC | java.lang.reflect.Modifier.FINAL |
//                    java.lang.reflect.Modifier.INTERFACE | java.lang.reflect.Modifier.ABSTRACT);

            /*
             * compensate for javac bug in which ABSTRACT bit was set for an
             * interface only if the interface declared methods
             */
            if (type.getKind() == ElementKind.INTERFACE) {
                if (ElementFilter.methodsIn(type.getEnclosedElements()).size() > 0) {
                    classMods |= java.lang.reflect.Modifier.ABSTRACT;
                } else {
                    classMods &= ~java.lang.reflect.Modifier.ABSTRACT;
                }
            }
//            Method[] methods = cl.getDeclaredMethods();
//            if ((classMods & Modifier.INTERFACE) != 0) {
//                classMods = (methods.length > 0) ?
//                    (classMods | Modifier.ABSTRACT) :
//                    (classMods & ~Modifier.ABSTRACT);
//            }

            dout.writeInt(classMods);

            if (!(type.asType() instanceof javax.lang.model.type.ArrayType)) {
//            if (!cl.isArray()) {
                /*
                 * compensate for change in 1.2FCS in which
                 * Class.getInterfaces() was modified to return Cloneable and
                 * Serializable for array classes.
                 */
                List<? extends TypeMirror> interfaces = type.getInterfaces();
                String[] ifaceNames = new String[interfaces.size()];
                for (int i = 0; i < interfaces.size(); i++) {
                    ifaceNames[i] = ((TypeElement) ((DeclaredType) interfaces.get(i)).asElement()).getQualifiedName()
                        .toString();
                }
//                Class[] interfaces = cl.getInterfaces();
//                String[] ifaceNames = new String[interfaces.length];
//                for (int i = 0; i < interfaces.length; i++) {
//                    ifaceNames[i] = interfaces[i].getName();
//                }
                Arrays.sort(ifaceNames);
                for (int i = 0; i < ifaceNames.length; i++) {
                    dout.writeUTF(ifaceNames[i]);
                }
            }

            //TODO remove this guy, only here for debug purposes
            Class cl;

            List<? extends VariableElement> fields = ElementFilter.fieldsIn(type.getEnclosedElements());
            MemberSignature[] fieldSigs = new MemberSignature[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                fieldSigs[i] = new MemberSignature(fields.get(i));
            }
//            Field[] fields = cl.getDeclaredFields();
//            MemberSignature[] fieldSigs = new MemberSignature[fields.length];
//            for (int i = 0; i < fields.length; i++) {
//                fieldSigs[i] = new MemberSignature(fields[i]);
//            }

            Arrays.sort(fieldSigs, new Comparator<MemberSignature>() {
                public int compare(MemberSignature o1, MemberSignature o2) {
                    String name1 = o1.name;
                    String name2 = o2.name;
                    return name1.compareTo(name2);
                }
            });
            for (int i = 0; i < fieldSigs.length; i++) {
                MemberSignature sig = fieldSigs[i];
                int mods = asReflectiveModifiers(sig.member, Modifier.PUBLIC, Modifier.PRIVATE, Modifier.PROTECTED,
                    Modifier.STATIC, Modifier.FINAL, Modifier.VOLATILE, Modifier.TRANSIENT);
//                int mods = sig.member.getModifiers() &
//                    (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED |
//                        Modifier.STATIC | Modifier.FINAL | Modifier.VOLATILE |
//                        Modifier.TRANSIENT);

                if (((mods & java.lang.reflect.Modifier.PRIVATE) == 0) ||
                    ((mods & (java.lang.reflect.Modifier.STATIC | java.lang.reflect.Modifier.TRANSIENT)) == 0)) {
                    dout.writeUTF(sig.name);
                    dout.writeInt(mods);
                    dout.writeUTF(sig.signature);
                }
            }
//
//            if (hasStaticInitializer(cl)) {
//                dout.writeUTF("<clinit>");
//                dout.writeInt(Modifier.STATIC);
//                dout.writeUTF("()V");
//            }
//
//            Constructor[] cons = cl.getDeclaredConstructors();
//            MemberSignature[] consSigs = new MemberSignature[cons.length];
//            for (int i = 0; i < cons.length; i++) {
//                consSigs[i] = new MemberSignature(cons[i]);
//            }
//            Arrays.sort(consSigs, new Comparator() {
//                public int compare(Object o1, Object o2) {
//                    String sig1 = ((MemberSignature) o1).signature;
//                    String sig2 = ((MemberSignature) o2).signature;
//                    return sig1.compareTo(sig2);
//                }
//            });
//            for (int i = 0; i < consSigs.length; i++) {
//                MemberSignature sig = consSigs[i];
//                int mods = sig.member.getModifiers() &
//                    (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED |
//                        Modifier.STATIC | Modifier.FINAL |
//                        Modifier.SYNCHRONIZED | Modifier.NATIVE |
//                        Modifier.ABSTRACT | Modifier.STRICT);
//                if ((mods & Modifier.PRIVATE) == 0) {
//                    dout.writeUTF("<init>");
//                    dout.writeInt(mods);
//                    dout.writeUTF(sig.signature.replace('/', '.'));
//                }
//            }
//
//            MemberSignature[] methSigs = new MemberSignature[methods.length];
//            for (int i = 0; i < methods.length; i++) {
//                methSigs[i] = new MemberSignature(methods[i]);
//            }
//            Arrays.sort(methSigs, new Comparator() {
//                public int compare(Object o1, Object o2) {
//                    MemberSignature ms1 = (MemberSignature) o1;
//                    MemberSignature ms2 = (MemberSignature) o2;
//                    int comp = ms1.name.compareTo(ms2.name);
//                    if (comp == 0) {
//                        comp = ms1.signature.compareTo(ms2.signature);
//                    }
//                    return comp;
//                }
//            });
//            for (int i = 0; i < methSigs.length; i++) {
//                MemberSignature sig = methSigs[i];
//                int mods = sig.member.getModifiers() &
//                    (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED |
//                        Modifier.STATIC | Modifier.FINAL |
//                        Modifier.SYNCHRONIZED | Modifier.NATIVE |
//                        Modifier.ABSTRACT | Modifier.STRICT);
//                if ((mods & Modifier.PRIVATE) == 0) {
//                    dout.writeUTF(sig.name);
//                    dout.writeInt(mods);
//                    dout.writeUTF(sig.signature.replace('/', '.'));
//                }
//            }
//
//            dout.flush();
//
//            MessageDigest md = MessageDigest.getInstance("SHA");
//            byte[] hashBytes = md.digest(bout.toByteArray());
//            long hash = 0;
//            for (int i = Math.min(hashBytes.length, 8) - 1; i >= 0; i--) {
//                hash = (hash << 8) | (hashBytes[i] & 0xFF);
//            }
//            return hash;

            return 42;
        } catch (IOException /* | NoSuchAlgorithmException */ ex) {
            throw new IllegalStateException(
                "Could not compute default serialization UID for class: " + type.getQualifiedName().toString(), ex);
        }
    }

    /**
     * Adapted from {@link java.io.ObjectStreamClass.MemberSignature}
     * <p/>
     * Class for computing and caching field/constructor/method signatures
     * during serialVersionUID calculation.
     */
    private static class MemberSignature {

        public final Element member;
        public final String name;
        public final String signature;

        public MemberSignature(VariableElement field) {
            member = field;
            name = field.getSimpleName().toString();
            signature = Util.toUniqueString(field.asType());
        }

        public MemberSignature(ExecutableElement meth) {
            member = meth;
            name = meth.getSimpleName().toString();
            signature = Util.toUniqueString(meth.asType());
        }
    }

    private static int asReflectiveModifiers(Element el, Modifier... applicableModifiers) {
        int mods = 0;
        for (Modifier m : applicableModifiers) {
            if (el.getModifiers().contains(m)) {
                switch (m) {
                case ABSTRACT:
                    mods |= java.lang.reflect.Modifier.ABSTRACT;
                    break;
                case FINAL:
                    mods |= java.lang.reflect.Modifier.FINAL;
                    break;
                case NATIVE:
                    mods |= java.lang.reflect.Modifier.NATIVE;
                    break;
                case PRIVATE:
                    mods |= java.lang.reflect.Modifier.PRIVATE;
                    break;
                case PROTECTED:
                    mods |= java.lang.reflect.Modifier.PROTECTED;
                    break;
                case PUBLIC:
                    mods |= java.lang.reflect.Modifier.PUBLIC;
                    break;
                case STATIC:
                    mods |= java.lang.reflect.Modifier.STATIC;
                    break;
                case STRICTFP:
                    mods |= java.lang.reflect.Modifier.STRICT;
                    break;
                case SYNCHRONIZED:
                    mods |= java.lang.reflect.Modifier.SYNCHRONIZED;
                    break;
                case TRANSIENT:
                    mods |= java.lang.reflect.Modifier.TRANSIENT;
                    break;
                case VOLATILE:
                    mods |= java.lang.reflect.Modifier.VOLATILE;
                    break;
                }
            }
        }

        return mods;
    }
}
