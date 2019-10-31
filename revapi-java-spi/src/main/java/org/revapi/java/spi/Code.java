/*
 * Copyright 2014-2018 Lukas Krejci
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
package org.revapi.java.spi;

import static org.revapi.CompatibilityType.BINARY;
import static org.revapi.CompatibilityType.SEMANTIC;
import static org.revapi.CompatibilityType.SOURCE;
import static org.revapi.DifferenceSeverity.BREAKING;
import static org.revapi.DifferenceSeverity.EQUIVALENT;
import static org.revapi.DifferenceSeverity.NON_BREAKING;
import static org.revapi.DifferenceSeverity.POTENTIALLY_BREAKING;

import java.lang.ref.WeakReference;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.WeakHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import org.revapi.CompatibilityType;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;

/**
 * The is a list of all difference codes Revapi's Java extension can emit. This can be used by others when they want to
 * override the default detection behavior by providing custom difference transforms.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public enum Code {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                MISSING_IN_OLD_API("java.missing.oldClass", POTENTIALLY_BREAKING, POTENTIALLY_BREAKING, null),
    MISSING_IN_NEW_API("java.missing.newClass", POTENTIALLY_BREAKING, POTENTIALLY_BREAKING, null),
    MISSING_OLD_SUPERTYPE("java.missing.oldSuperType", POTENTIALLY_BREAKING, POTENTIALLY_BREAKING,
            null, "superClass"),
    MISSING_NEW_SUPERTYPE("java.missing.newSuperType", POTENTIALLY_BREAKING, POTENTIALLY_BREAKING,
            null, "superClass"),

    ELEMENT_NO_LONGER_DEPRECATED("java.element.noLongerDeprecated", EQUIVALENT, EQUIVALENT, null),
    ELEMENT_NOW_DEPRECATED("java.element.nowDeprecated", EQUIVALENT, EQUIVALENT, null),

    CLASS_VISIBILITY_INCREASED("java.class.visibilityIncreased", EQUIVALENT, EQUIVALENT, null),
    CLASS_VISIBILITY_REDUCED("java.class.visibilityReduced", BREAKING, BREAKING, null),
    CLASS_KIND_CHANGED("java.class.kindChanged", BREAKING, BREAKING, null),
    CLASS_NO_LONGER_FINAL("java.class.noLongerFinal", EQUIVALENT, EQUIVALENT, null),
    CLASS_NOW_FINAL("java.class.nowFinal", BREAKING, BREAKING, null),
    CLASS_NO_LONGER_ABSTRACT("java.class.noLongerAbstract", EQUIVALENT, EQUIVALENT, null),
    CLASS_NOW_ABSTRACT("java.class.nowAbstract", BREAKING, BREAKING, null),
    CLASS_ADDED("java.class.added", NON_BREAKING, NON_BREAKING, null),
    CLASS_REMOVED("java.class.removed", BREAKING, BREAKING, null),
    CLASS_NO_LONGER_IMPLEMENTS_INTERFACE("java.class.noLongerImplementsInterface", BREAKING, BREAKING, null, "interface"),
    CLASS_NOW_IMPLEMENTS_INTERFACE("java.class.nowImplementsInterface", NON_BREAKING, NON_BREAKING, null, "interface"),
    CLASS_FINAL_CLASS_INHERITS_FROM_NEW_CLASS("java.class.finalClassInheritsFromNewClass", EQUIVALENT, EQUIVALENT,
        null, "superClass"),
    CLASS_NON_FINAL_CLASS_INHERITS_FROM_NEW_CLASS("java.class.nonFinalClassInheritsFromNewClass", POTENTIALLY_BREAKING,
        POTENTIALLY_BREAKING, null, "superClass"),
    CLASS_NOW_CHECKED_EXCEPTION("java.class.nowCheckedException", BREAKING, NON_BREAKING, null),
    CLASS_NO_LONGER_INHERITS_FROM_CLASS("java.class.noLongerInheritsFromClass", BREAKING, BREAKING, null),
    CLASS_NON_PUBLIC_PART_OF_API("java.class.nonPublicPartOfAPI", NON_BREAKING, NON_BREAKING, BREAKING),
    CLASS_SUPER_TYPE_TYPE_PARAMETERS_CHANGED("java.class.superTypeTypeParametersChanged", POTENTIALLY_BREAKING,
        POTENTIALLY_BREAKING, null, "oldSuperType", "newSuperType"),
    CLASS_EXTERNAL_CLASS_EXPOSED_IN_API("java.class.externalClassExposedInAPI", NON_BREAKING, NON_BREAKING,
            POTENTIALLY_BREAKING),
    CLASS_EXTERNAL_CLASS_NO_LONGER_EXPOSED_IN_API("java.class.externalClassNoLongerExposedInAPI", NON_BREAKING,
            NON_BREAKING, null),
    CLASS_DEFAULT_SERIALIZATION_CHANGED("java.class.defaultSerializationChanged", EQUIVALENT, EQUIVALENT,
            BREAKING),

    ANNOTATION_ADDED("java.annotation.added", EQUIVALENT, EQUIVALENT, POTENTIALLY_BREAKING, "annotation"),
    ANNOTATION_REMOVED("java.annotation.removed", EQUIVALENT, EQUIVALENT, POTENTIALLY_BREAKING, "annotation"),
    ANNOTATION_ATTRIBUTE_VALUE_CHANGED("java.annotation.attributeValueChanged", EQUIVALENT, EQUIVALENT,
        POTENTIALLY_BREAKING, "annotationType", "attribute", "oldValue", "newValue"),
    ANNOTATION_ATTRIBUTE_ADDED("java.annotation.attributeAdded", EQUIVALENT, EQUIVALENT, POTENTIALLY_BREAKING,
            "annotation", "attribute"),
    ANNOTATION_ATTRIBUTE_REMOVED("java.annotation.attributeRemoved", EQUIVALENT, EQUIVALENT, POTENTIALLY_BREAKING,
            "annotation", "attribute"),
    ANNOTATION_NO_LONGER_INHERITED("java.annotation.noLongerInherited", NON_BREAKING, NON_BREAKING,
        POTENTIALLY_BREAKING, "annotationType"),
    ANNOTATION_NOW_INHERITED("java.annotation.nowInherited", NON_BREAKING, NON_BREAKING, POTENTIALLY_BREAKING,
            "annotationType"),
    ANNOTATION_NO_LONGER_PRESENT("java.annotation.noLongerPresent", BREAKING, BREAKING, POTENTIALLY_BREAKING),

    FIELD_ADDED_STATIC_FIELD("java.field.addedStaticField", NON_BREAKING, NON_BREAKING, null),
    FIELD_ADDED("java.field.added", NON_BREAKING, NON_BREAKING, null),
    FIELD_REMOVED("java.field.removed", BREAKING, BREAKING, null),
    FIELD_MOVED_TO_SUPER_CLASS("java.field.movedToSuperClass", EQUIVALENT, EQUIVALENT, null),
    FIELD_INHERITED_NOW_DECLARED("java.field.inheritedNowDeclared", EQUIVALENT, EQUIVALENT, null),
    FIELD_CONSTANT_REMOVED("java.field.removedWithConstant", BREAKING, NON_BREAKING, POTENTIALLY_BREAKING),
    FIELD_CONSTANT_VALUE_CHANGED("java.field.constantValueChanged", NON_BREAKING, NON_BREAKING, BREAKING),
    FIELD_NOW_CONSTANT("java.field.nowConstant", EQUIVALENT, EQUIVALENT, POTENTIALLY_BREAKING),
    FIELD_NO_LONGER_CONSTANT("java.field.noLongerConstant", EQUIVALENT, EQUIVALENT, BREAKING),
    FIELD_NOW_FINAL("java.field.nowFinal", POTENTIALLY_BREAKING, POTENTIALLY_BREAKING, null),
    FIELD_NO_LONGER_FINAL("java.field.noLongerFinal", NON_BREAKING, NON_BREAKING, null),
    FIELD_NO_LONGER_STATIC("java.field.noLongerStatic", BREAKING, BREAKING, null),
    FIELD_NOW_STATIC("java.field.nowStatic", NON_BREAKING, BREAKING, null),
    FIELD_TYPE_CHANGED("java.field.typeChanged", BREAKING, BREAKING, null),
    FIELD_SERIAL_VERSION_UID_UNCHANGED("java.field.serialVersionUIDUnchanged", EQUIVALENT, EQUIVALENT,
        POTENTIALLY_BREAKING, "serialVersionUID"),
    FIELD_SERIAL_VERSION_UID_CHANGED("java.field.serialVersionUIDChanged", EQUIVALENT, EQUIVALENT, BREAKING,
            "oldSerialVersionUID", "newSerialVersionUID"),
    FIELD_VISIBILITY_INCREASED("java.field.visibilityIncreased", EQUIVALENT, EQUIVALENT, null, "oldVisibility",
            "newVisibility"),
    FIELD_VISIBILITY_REDUCED("java.field.visibilityReduced", BREAKING, BREAKING, null, "oldVisibility",
            "newVisibility"),
    FIELD_ENUM_CONSTANT_ORDER_CHANGED("java.field.enumConstantOrderChanged", NON_BREAKING, NON_BREAKING,
            POTENTIALLY_BREAKING),

    METHOD_DEFAULT_VALUE_ADDED("java.method.defaultValueAdded", NON_BREAKING, NON_BREAKING, null),
    METHOD_DEFAULT_VALUE_CHANGED("java.method.defaultValueChanged", NON_BREAKING, NON_BREAKING, POTENTIALLY_BREAKING,
            "oldValue", "newValue"),
    METHOD_DEFAULT_VALUE_REMOVED("java.method.defaultValueRemoved", BREAKING, NON_BREAKING, BREAKING),
    METHOD_ADDED_TO_INTERFACE("java.method.addedToInterface", BREAKING, NON_BREAKING, POTENTIALLY_BREAKING),
    METHOD_DEFAULT_METHOD_ADDED_TO_INTERFACE("java.method.defaultMethodAddedToInterface", NON_BREAKING, NON_BREAKING,
            null),
    METHOD_STATIC_METHOD_ADDED_TO_INTERFACE("java.method.staticMethodAddedToInterface", NON_BREAKING, NON_BREAKING,
            null),
    METHOD_ATTRIBUTE_WITH_NO_DEFAULT_ADDED_TO_ANNOTATION_TYPE("java.method.attributeWithNoDefaultAddedToAnnotationType",
        BREAKING, NON_BREAKING, BREAKING),
    METHOD_ATTRIBUTE_WITH_DEFAULT_ADDED_TO_ANNOTATION_TYPE("java.method.attributeWithDefaultAddedToAnnotationType",
        NON_BREAKING, NON_BREAKING, null),
    METHOD_ABSTRACT_METHOD_ADDED("java.method.abstractMethodAdded", BREAKING, BREAKING, null),
    METHOD_ADDED("java.method.added", NON_BREAKING, NON_BREAKING, null),
    METHOD_FINAL_METHOD_ADDED_TO_NON_FINAL_CLASS("java.method.finalMethodAddedToNonFinalClass", POTENTIALLY_BREAKING,
        POTENTIALLY_BREAKING, null),
    METHOD_REMOVED("java.method.removed", BREAKING, BREAKING, null),
    METHOD_MOVED_TO_SUPERCLASS("java.method.movedToSuperClass", EQUIVALENT, EQUIVALENT, null),
    METHOD_INHERITED_METHOD_MOVED_TO_CLASS("java.method.inheritedMovedToClass", EQUIVALENT, EQUIVALENT, null),
    METHOD_ATTRIBUTE_REMOVED_FROM_ANNOTATION_TYPE(
        "java.method.attributeRemovedFromAnnotationType", BREAKING, BREAKING, null),
    METHOD_NO_LONGER_FINAL("java.method.noLongerFinal", NON_BREAKING, NON_BREAKING, null),
    METHOD_NOW_FINAL("java.method.nowFinal", POTENTIALLY_BREAKING, POTENTIALLY_BREAKING, null),
    METHOD_NOW_FINAL_IN_FINAL_CLASS("java.method.nowFinalInFinalClass", EQUIVALENT, EQUIVALENT, null),
    METHOD_VISIBILITY_INCREASED("java.method.visibilityIncreased", EQUIVALENT, EQUIVALENT, null, "oldVisibility",
            "newVisibility"),
    METHOD_VISIBILITY_REDUCED("java.method.visibilityReduced", BREAKING, BREAKING, null, "oldVisibility",
            "newVisibility"),
    METHOD_RETURN_TYPE_CHANGED("java.method.returnTypeChanged", POTENTIALLY_BREAKING, BREAKING, null),
    METHOD_RETURN_TYPE_TYPE_PARAMETERS_CHANGED("java.method.returnTypeTypeParametersChanged", BREAKING,
        NON_BREAKING, null),
    METHOD_RETURN_TYPE_CHANGED_COVARIANTLY("java.method.returnTypeChangedCovariantly", NON_BREAKING, BREAKING, null),
    METHOD_NUMBER_OF_PARAMETERS_CHANGED("java.method.numberOfParametersChanged", BREAKING, BREAKING, null),
    METHOD_PARAMETER_TYPE_CHANGED("java.method.parameterTypeChanged", POTENTIALLY_BREAKING, BREAKING, null),
    METHOD_PARAMETER_TYPE_PARAMETER_CHANGED("java.method.parameterTypeParameterChanged", POTENTIALLY_BREAKING,
            NON_BREAKING, null),
    METHOD_NO_LONGER_STATIC("java.method.noLongerStatic", BREAKING, BREAKING, null),
    METHOD_NOW_STATIC("java.method.nowStatic", NON_BREAKING, BREAKING, null),
    METHOD_CHECKED_EXCEPTION_ADDED("java.method.exception.checkedAdded", BREAKING, NON_BREAKING, null, "exception"),
    METHOD_RUNTIME_EXCEPTION_ADDED("java.method.exception.runtimeAdded", NON_BREAKING, NON_BREAKING, POTENTIALLY_BREAKING,
            "exception"),
    METHOD_CHECKED_EXCEPTION_REMOVED("java.method.exception.checkedRemoved", BREAKING, NON_BREAKING, null, "exception"),
    METHOD_RUNTIME_EXCEPTION_REMOVED("java.method.exception.runtimeRemoved", NON_BREAKING, NON_BREAKING, null,
            "exception"),
    METHOD_NO_LONGER_DEFAULT("java.method.noLongerDefault", BREAKING, BREAKING, null),
    METHOD_NOW_DEFAULT("java.method.nowDefault", EQUIVALENT, EQUIVALENT, null),
    METHOD_NOW_ABSTRACT("java.method.nowAbstract", BREAKING, BREAKING, null),
    METHOD_NO_LONGER_ABSTRACT("java.method.noLongerAbstract", EQUIVALENT, EQUIVALENT, null),

    GENERICS_ELEMENT_NOW_PARAMETERIZED("java.generics.elementNowParameterized", NON_BREAKING, NON_BREAKING,
        POTENTIALLY_BREAKING),
    GENERICS_FORMAL_TYPE_PARAMETER_ADDED("java.generics.formalTypeParameterAdded", BREAKING, NON_BREAKING, null,
            "typeParameter"),
    GENERICS_FORMAL_TYPE_PARAMETER_REMOVED("java.generics.formalTypeParameterRemoved", BREAKING, NON_BREAKING, null,
            "typeParameter"),
    GENERICS_FORMAL_TYPE_PARAMETER_CHANGED("java.generics.formalTypeParameterChanged", BREAKING, NON_BREAKING, null,
            "typeParameter");

    private final String code;
    private final EnumMap<CompatibilityType, DifferenceSeverity> classification;
    private final List<String> identifyingAttachments;

    Code(String code, DifferenceSeverity sourceSeverity, DifferenceSeverity binarySeverity,
        DifferenceSeverity semanticSeverity, String... identifyingAttachments) {
        this.code = code;
        classification = new EnumMap<>(CompatibilityType.class);
        addClassification(SOURCE, sourceSeverity);
        addClassification(BINARY, binarySeverity);
        addClassification(SEMANTIC, semanticSeverity);
        this.identifyingAttachments = Collections.unmodifiableList(Arrays.asList(identifyingAttachments));
    }

    @SuppressWarnings("UnusedDeclaration")
    public static Code fromCode(String code) {
        for (Code c : Code.values()) {
            if (c.code.equals(code)) {
                return c;
            }
        }

        return null;
    }

    public static <T extends JavaElement>
    LinkedHashMap<String, String> attachmentsFor(@Nullable T oldElement, @Nullable T newElement, String... customAttachments) {
        T representative = oldElement == null ? newElement : oldElement;
        if (representative == null) {
            throw new IllegalArgumentException("At least one of the oldElement and newElement must not be null");
        }

        LinkedHashMap<String, String> ret = keyVals(customAttachments);
        final boolean addElementKind;
        if (representative instanceof JavaAnnotationElement) {
            //annotationType
            JavaAnnotationElement anno = representative.as(JavaAnnotationElement.class);
            addElementKind = false;
            ret.put("annotationType", Util.toHumanReadableString(anno.getAnnotation().getAnnotationType()));
            ret.put("elementKind", "annotation");
        } else if (representative instanceof JavaFieldElement) {
            //package, classSimpleName, fieldName
            JavaFieldElement field = representative.as(JavaFieldElement.class);
            addElementKind = true;
            ret.put("package", getPackageName(field));
            ret.put("classQualifiedName", getClassQualifiedName(field));
            ret.put("classSimpleName", getClassSimpleName(field));
            ret.put("fieldName", field.getDeclaringElement().getSimpleName().toString());
        } else if (representative instanceof JavaTypeElement) {
            //package, classSimpleName
            JavaTypeElement type = representative.as(JavaTypeElement.class);
            addElementKind = true;
            ret.put("package", getPackageName(type));
            ret.put("classQualifiedName", getClassQualifiedName(type));
            ret.put("classSimpleName", getClassSimpleName(type));
        } else if (representative instanceof JavaMethodElement) {
            //package, classSimpleName, methodName
            JavaMethodElement method = representative.as(JavaMethodElement.class);
            addElementKind = true;
            ret.put("package", getPackageName(method));
            ret.put("classQualifiedName", getClassQualifiedName(method));
            ret.put("classSimpleName", getClassSimpleName(method));
            ret.put("methodName", method.getDeclaringElement().getSimpleName().toString());
        } else if (representative instanceof JavaMethodParameterElement) {
            //package, classSimpleName, methodName, parameterIndex
            JavaMethodParameterElement param = (JavaMethodParameterElement) representative;
            @SuppressWarnings("ConstantConditions")
            JavaMethodElement method = representative.getParent().as(JavaMethodElement.class);
            addElementKind = true;
            ret.put("package", getPackageName(method));
            ret.put("classQualifiedName", getClassQualifiedName(method));
            ret.put("classSimpleName", getClassSimpleName(method));
            ret.put("methodName", method.getDeclaringElement().getSimpleName().toString());
            ret.put("parameterIndex", Integer.toString(param.getIndex()));
        } else {
            addElementKind = false;
        }

        if (oldElement != null && oldElement.getArchive() != null) {
            ret.put("oldArchive", oldElement.getArchive().getName());
        }

        if (newElement != null && newElement.getArchive() != null) {
            ret.put("newArchive", newElement.getArchive().getName());
        }

        if (addElementKind) {
            String kind;
            ElementKind elementKind = ((JavaModelElement) representative).getDeclaringElement().getKind();
            switch (elementKind) {
                case ANNOTATION_TYPE:
                    kind = "@interface";
                    break;
                case CLASS:
                    kind = "class";
                    break;
                case CONSTRUCTOR:
                    kind = "constructor";
                    break;
                case ENUM:
                    kind = "enum";
                    break;
                case ENUM_CONSTANT:
                    kind = "enumConstant";
                    break;
                case FIELD:
                    kind = "field";
                    break;
                case INSTANCE_INIT:
                    //this most probably never occurs
                    kind = "initializer";
                    break;
                case INTERFACE:
                    kind = "interface";
                    break;
                case METHOD:
                    kind = "method";
                    break;
                case PACKAGE:
                    //this never occurs, because we don't support explicit checks on packages yet
                    kind = "package";
                    break;
                case PARAMETER:
                    kind = "parameter";
                    break;
                case STATIC_INIT:
                    //this most probably never occurs
                    kind = "staticInitializer";
                    break;
                case TYPE_PARAMETER:
                    //this most probably never occurs, because we don't do checks directly on type params, but rather
                    kind = "typeParameter";
                    break;
                default:
                    kind = "unknownKind(" + elementKind + ")";
            }
            ret.put("elementKind", kind);
        }

        return ret;
    }

    private static String getPackageName(JavaModelElement element) {
        while (element != null && !(element instanceof JavaTypeElement)) {
            element = element.getParent();
        }

        if (element == null) {
            return "";
        } else {
            TypeElement type = element.as(JavaTypeElement.class).getDeclaringElement();
            Element pkg = type.getEnclosingElement();
            while (pkg != null && pkg.getKind() != ElementKind.PACKAGE) {
                pkg = pkg.getEnclosingElement();
            }

            if (pkg == null) {
                return "";
            } else {
                return ((PackageElement) pkg).getQualifiedName().toString();
            }
        }
    }

    private static String getClassSimpleName(JavaModelElement element) {
        TypeElement declaringClass = getDeclaringClass(element);
        return declaringClass == null ? null : declaringClass.getSimpleName().toString();
    }

    private static String getClassQualifiedName(JavaModelElement element) {
        TypeElement declaringClass = getDeclaringClass(element);
        return declaringClass == null ? null : declaringClass.getQualifiedName().toString();
    }

    private static TypeElement getDeclaringClass(JavaModelElement element) {
        while (element != null && !(element instanceof JavaTypeElement)) {
            element = element.getParent();
        }
        return element == null ? null : element.as(JavaTypeElement.class).getDeclaringElement();
    }

    public String code() {
        return code;
    }

    public Difference createDifference(@Nonnull Locale locale) {
        Message message = getMessages(locale).get(code);
        Difference.Builder bld = Difference.builder().withCode(code).withName(message.name)
            .withDescription(message.description);
        for (Map.Entry<CompatibilityType, DifferenceSeverity> e : classification.entrySet()) {
            bld.addClassification(e.getKey(), e.getValue());
        }

        return bld.build();
    }

    public Difference createDifference(@Nonnull Locale locale, LinkedHashMap<String, String> attachments) {
        String[] params = attachments.values().toArray(new String[attachments.size()]);
        return createDifference(locale, attachments, params);
    }

    public Difference createDifference(@Nonnull Locale locale, LinkedHashMap<String, String> attachments,
                                       String... parameters) {
        Message message = getMessages(locale).get(code);
        String description = MessageFormat.format(message.description, (Object[]) parameters);
        Difference.Builder bld = Difference.builder().withCode(code).withName(message.name)
                .withDescription(description).addAttachments(attachments)
                .withIdentifyingAttachments(identifyingAttachments);

        for (Map.Entry<CompatibilityType, DifferenceSeverity> e : classification.entrySet()) {
            bld.addClassification(e.getKey(), e.getValue());
        }

        return bld.build();
    }

    private static LinkedHashMap<String, String> keyVals(String... keyVals) {
        if (keyVals.length % 2 != 0) {
            throw new IllegalArgumentException("Uneven key-value pairs.");
        }

        LinkedHashMap<String, String> ret = new LinkedHashMap<>(keyVals.length / 2);
        String currentKey = null;
        for (int i = 0; i < keyVals.length; ++i) {
            String x = keyVals[i];

            if (x == null) {
                throw new IllegalArgumentException("Null keys or values not supported in attachments.");
            }

            if (i % 2  == 0) {
                currentKey = keyVals[i];
            } else {
                ret.put(currentKey, x);
            }
        }

        return ret;
    }

    private static class Message {
        final String name;
        final String description;

        private Message(String name, String description) {
            this.description = description;
            this.name = name;
        }
    }

    private static class Messages {

        private final ResourceBundle names;
        private final ResourceBundle descriptions;

        public Messages(Locale locale) {
            descriptions = ResourceBundle.getBundle("org.revapi.java.checks.descriptions", locale);
            names = ResourceBundle.getBundle("org.revapi.java.checks.names", locale);
        }

        Message get(String key) {
            String name = names.getString(key);
            String description = descriptions.getString(key);
            return new Message(name, description);
        }
    }

    private static WeakHashMap<Locale, WeakReference<Messages>> messagesCache = new WeakHashMap<>();

    private static synchronized Messages getMessages(Locale locale) {
        WeakReference<Messages> messageRef = messagesCache.get(locale);
        if (messageRef == null || messageRef.get() == null) {
            messageRef = new WeakReference<>(new Messages(locale));
            messagesCache.put(locale, messageRef);
        }

        return messageRef.get();
    }

    private void addClassification(CompatibilityType compatibilityType, DifferenceSeverity severity) {
        if (severity != null) {
            classification.put(compatibilityType, severity);
        }
    }
}
