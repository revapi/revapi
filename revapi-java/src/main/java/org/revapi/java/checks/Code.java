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

import java.lang.ref.WeakReference;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.WeakHashMap;

import org.revapi.MatchReport;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public enum Code {
    CLASS_VISIBILITY_INCREASED("java.class.visibilityIncreased"),
    CLASS_VISIBILITY_REDUCED("java.class.visibilityReduced"),
    CLASS_KIND_CHANGED("java.class.kindChanged"),
    CLASS_NO_LONGER_FINAL("java.class.noLongerFinal"),
    CLASS_NOW_FINAL("java.class.nowFinal"),
    CLASS_NO_LONGER_ABSTRACT("java.class.noLongerAbstract"),
    CLASS_NOW_ABSTRACT("java.class.nowAbstract"),
    CLASS_ADDED("java.class.added"),
    CLASS_REMOVED("java.class.removed"),
    CLASS_NO_LONGER_IMPLEMENTS_INTERFACE("java.class.noLongerImplementsInterface"),
    CLASS_NOW_IMPLEMENTS_INTERFACE("java.class.nowImplementsInterface"),
    CLASS_INHERITS_FROM_NEW_CLASS("java.class.inheritsFromNewClass"),
    CLASS_NO_LONGER_INHERITS_FROM_CLASS("java.class.noLongerInheritsFromClass"),

    ANNOTATION_ADDED("java.annotation.added"),
    ANNOTATION_REMOVED("java.annotation.removed"),
    ANNOTATION_ATTRIBUTE_VALUE_CHANGED("java.annotation.attributeValueChanged"),
    ANNOTATION_ATTRIBUTE_ADDED("java.annotation.attributeAdded"),
    ANNOTATION_ATTRIBUTE_REMOVED("java.annotation.attributeRemoved"),
    ANNOTATION_NO_LONGER_INHERITED("java.annotation.noLongerInherited"),
    ANNOTATION_NOW_INHERITED("java.annotation.nowInherited"),

    METHOD_DEFAULT_VALUE_CHANGED("java.method.defaultValueChanged");

    public static Code fromCode(String code) {
        for (Code c : Code.values()) {
            if (c.code.equals(code)) {
                return c;
            }
        }

        return null;
    }

    private final String code;

    private Code(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public MatchReport.Problem.Builder initializeNewProblem(Locale locale) {
        Message message = getMessages(locale).get(code);
        return MatchReport.Problem.create().withCode(code).withName(message.name).withDescription(message.description);
    }

    public MatchReport.Problem.Builder initializeNewProblem(Locale locale, Object[] params, Object... attachments) {
        Message message = getMessages(locale).get(code);
        String description = MessageFormat.format(message.description, params);
        return MatchReport.Problem.create().withCode(code).withName(message.name).withDescription(description)
            .addAttachments(attachments);
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
}
