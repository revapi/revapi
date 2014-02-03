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

package org.revapi.java.checks;

import static org.revapi.ChangeSeverity.BREAKING;
import static org.revapi.ChangeSeverity.NON_BREAKING;
import static org.revapi.ChangeSeverity.POTENTIALLY_BREAKING;
import static org.revapi.CompatibilityType.BINARY;
import static org.revapi.CompatibilityType.SEMANTIC;
import static org.revapi.CompatibilityType.SOURCE;

import java.lang.ref.WeakReference;
import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.WeakHashMap;

import org.revapi.ChangeSeverity;
import org.revapi.CompatibilityType;
import org.revapi.MatchReport;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public enum Code {
    CLASS_VISIBILITY_INCREASED("java.class.visibilityIncreased", NON_BREAKING, NON_BREAKING, null),
    CLASS_VISIBILITY_REDUCED("java.class.visibilityReduced", BREAKING, BREAKING, null),
    CLASS_KIND_CHANGED("java.class.kindChanged", BREAKING, BREAKING, null),
    CLASS_NO_LONGER_FINAL("java.class.noLongerFinal", NON_BREAKING, NON_BREAKING, null),
    CLASS_NOW_FINAL("java.class.nowFinal", BREAKING, BREAKING, null),
    CLASS_NO_LONGER_ABSTRACT("java.class.noLongerAbstract", NON_BREAKING, NON_BREAKING, null),
    CLASS_NOW_ABSTRACT("java.class.nowAbstract", BREAKING, BREAKING, null),
    CLASS_ADDED("java.class.added", NON_BREAKING, NON_BREAKING, null),
    CLASS_REMOVED("java.class.removed", BREAKING, BREAKING, null),
    CLASS_NO_LONGER_IMPLEMENTS_INTERFACE("java.class.noLongerImplementsInterface", BREAKING, BREAKING, null),
    CLASS_NOW_IMPLEMENTS_INTERFACE("java.class.nowImplementsInterface", NON_BREAKING, NON_BREAKING, null),
    @Deprecated
    CLASS_INHERITS_FROM_NEW_CLASS("java.class.inheritsFromNewClass", NON_BREAKING, NON_BREAKING, null),
    CLASS_FINAL_CLASS_INHERITS_FROM_NEW_CLASS("java.class.finalClassInheritsFromNewClass", NON_BREAKING, NON_BREAKING,
        null), //TODO implement
    CLASS_NON_FINAL_CLASS_INHERITS_FROM_NEW_CLASS("java.class.nonFinalClassInheritsFromNewClass", POTENTIALLY_BREAKING,
        POTENTIALLY_BREAKING, POTENTIALLY_BREAKING), //TODO implement
    CLASS_NOW_CHECKED_EXCEPTION("java.class.nowCheckedException", BREAKING, NON_BREAKING, null), //TODO implement
    CLASS_NO_LONGER_INHERITS_FROM_CLASS("java.class.noLongerInheritsFromClass", BREAKING, BREAKING, null),

    ANNOTATION_ADDED("java.annotation.added", null, null, POTENTIALLY_BREAKING),
    ANNOTATION_REMOVED("java.annotation.removed", null, null, POTENTIALLY_BREAKING),
    ANNOTATION_ATTRIBUTE_VALUE_CHANGED("java.annotation.attributeValueChanged", null, null, POTENTIALLY_BREAKING),
    ANNOTATION_ATTRIBUTE_ADDED("java.annotation.attributeAdded", null, null, POTENTIALLY_BREAKING),
    ANNOTATION_ATTRIBUTE_REMOVED("java.annotation.attributeRemoved", null, null, POTENTIALLY_BREAKING),
    ANNOTATION_NO_LONGER_INHERITED("java.annotation.noLongerInherited", null, null, POTENTIALLY_BREAKING),
    ANNOTATION_NOW_INHERITED("java.annotation.nowInherited", null, null, POTENTIALLY_BREAKING),

    FIELD_ADDED_IN_FINAL_CLASS("java.field.addedInFinalClass", NON_BREAKING, NON_BREAKING, null),

    //TODO can this really break a caller that calls a subclass through the super-class variable?
    FIELD_ADDED_IN_NON_FINAL_CLASS("java.field.addedInNonFinalClass", NON_BREAKING, NON_BREAKING, POTENTIALLY_BREAKING),

    FIELD_REMOVED("java.field.removed", BREAKING, BREAKING, null),
    FIELD_CONSTANT_REMOVED("java.field.removedWithConstant", BREAKING, NON_BREAKING, POTENTIALLY_BREAKING),
    FIELD_CONSTANT_VALUE_CHANGED("java.field.constantValueChanged", NON_BREAKING, NON_BREAKING, BREAKING),
    FIELD_NOW_CONSTANT("java.field.nowConstant", NON_BREAKING, NON_BREAKING, null),
    FIELD_NO_LONGER_CONSTANT("java.field.noLongerConstant", NON_BREAKING, NON_BREAKING, BREAKING),
    FIELD_NOW_FINAL("java.field.nowFinal", BREAKING, BREAKING, null),
    FIELD_NO_LONGER_FINAL("java.field.noLongerFinal", NON_BREAKING, NON_BREAKING, null),
    FIELD_NO_LONGER_STATIC("java.field.noLongerStatic", BREAKING, BREAKING, null),
    FIELD_NOW_STATIC("java.field.nowStatic", BREAKING, BREAKING, null),
    FIELD_TYPE_CHANGED("java.field.typeChanged", BREAKING, BREAKING, null),
    FIELD_VISIBILITY_INCREASED("java.field.visibilityIncreased", NON_BREAKING, NON_BREAKING, null),
    FIELD_VISIBILITY_REDUCED("java.field.visibilityReduced", BREAKING, BREAKING, null),

    METHOD_DEFAULT_VALUE_CHANGED("java.method.defaultValueChanged", null, null, POTENTIALLY_BREAKING);

    private final String code;
    private final EnumMap<CompatibilityType, ChangeSeverity> classification;

    private Code(String code, ChangeSeverity sourceSeverity, ChangeSeverity binarySeverity,
        ChangeSeverity semanticSeverity) {
        this.code = code;
        classification = new EnumMap<>(CompatibilityType.class);
        addClassification(SOURCE, sourceSeverity);
        addClassification(BINARY, binarySeverity);
        addClassification(SEMANTIC, semanticSeverity);
    }

    public static Code fromCode(String code) {
        for (Code c : Code.values()) {
            if (c.code.equals(code)) {
                return c;
            }
        }

        return null;
    }

    public String code() {
        return code;
    }

    public MatchReport.Problem createProblem(Locale locale) {
        Message message = getMessages(locale).get(code);
        MatchReport.Problem.Builder bld = MatchReport.Problem.create().withCode(code).withName(message.name)
            .withDescription(message.description);
        for (Map.Entry<CompatibilityType, ChangeSeverity> e : classification.entrySet()) {
            bld.addClassification(e.getKey(), e.getValue());
        }

        return bld.build();
    }

    public MatchReport.Problem createProblem(Locale locale, Object[] params, Object... attachments) {
        Message message = getMessages(locale).get(code);
        String description = MessageFormat.format(message.description, params);
        MatchReport.Problem.Builder bld = MatchReport.Problem.create().withCode(code).withName(message.name)
            .withDescription(description).addAttachments(attachments);

        for (Map.Entry<CompatibilityType, ChangeSeverity> e : classification.entrySet()) {
            bld.addClassification(e.getKey(), e.getValue());
        }

        return bld.build();

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

    private void addClassification(CompatibilityType compatibilityType, ChangeSeverity severity) {
        if (severity != null) {
            classification.put(compatibilityType, severity);
        }
    }
}
