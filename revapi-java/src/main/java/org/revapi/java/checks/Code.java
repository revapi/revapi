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
 * @since 1.0
 */
public enum Code {
    CLASS_VISIBILITY_INCREASED(1),
    CLASS_VISIBILITY_REDUCED(2),
    CLASS_KIND_CHANGED(3),
    CLASS_NO_LONGER_FINAL(4),
    CLASS_NOW_FINAL(5),
    CLASS_NO_LONGER_ABSTRACT(6),
    CLASS_NOW_ABSTRACT(7),;

    private final String code;

    private Code(int number) {
        code = format(number);
    }

    public String code() {
        return code;
    }

    private static String format(int number) {
        return String.format("JAVA-%1$04d", number);
    }

    private static class Message {
        final String name;
        final String description;

        private Message(String description, String name) {
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

    public MatchReport.Problem.Builder initializeNewProblem(Locale locale) {
        Message message = getMessages(locale).get(code);
        return MatchReport.Problem.create().withCode(code).withName(message.name).withDescription(message.description);
    }

    public MatchReport.Problem.Builder initializeNewProblem(Locale locale, Object... params) {
        Message message = getMessages(locale).get(code);
        String description = MessageFormat.format(message.description, params);
        return MatchReport.Problem.create().withCode(code).withName(message.name).withDescription(description);
    }
}
