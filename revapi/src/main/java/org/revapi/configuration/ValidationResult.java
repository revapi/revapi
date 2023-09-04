/*
 * Copyright 2014-2023 Lukas Krejci
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
package org.revapi.configuration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.networknt.schema.ValidationMessage;

/**
 * Represents the results of the the configuration validation.
 *
 * @author Lukas Krejci
 *
 * @since 0.1
 */
public final class ValidationResult {
    public static final class Error {
        public final int code;
        public final String message;
        public final String dataPath;

        public Error(int code, String message, String dataPath) {
            this.code = code;
            this.message = message;
            this.dataPath = dataPath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Error error = (Error) o;

            if (code != error.code) {
                return false;
            }

            if (!dataPath.equals(error.dataPath)) {
                return false;
            }

            return message.equals(error.message);
        }

        @Override
        public int hashCode() {
            int result = code;
            result = 31 * result + message.hashCode();
            result = 31 * result + dataPath.hashCode();
            return result;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Error[");
            sb.append("code=").append(code);
            sb.append(", dataPath='").append(dataPath).append('\'');
            sb.append(", message='").append(message).append('\'');
            sb.append(']');
            return sb.toString();
        }
    }

    /**
     * @return new validation result representing successful validation.
     */
    public static ValidationResult success() {
        return new ValidationResult(null, null);
    }

    private final String[] missingSchemas;
    private final Error[] errors;

    public ValidationResult(@Nullable String[] missingSchemas, @Nullable Error[] errors) {
        this.missingSchemas = missingSchemas;
        this.errors = errors;
    }

    static ValidationResult fromValidationMessages(Set<ValidationMessage> validationMessages) {
        return new ValidationResult(null,
                validationMessages.stream().map(vm -> new ValidationResult.Error(0, vm.getMessage(), vm.getPath()))
                        .toArray(ValidationResult.Error[]::new));
    }

    public ValidationResult merge(ValidationResult other) {
        if (missingSchemas == null && errors == null) {
            return other;
        }

        if (other.missingSchemas == null && other.errors == null) {
            return this;
        }

        HashSet<String> newMissingSchemas = missingSchemas == null ? null
                : new HashSet<>(Arrays.asList(missingSchemas));

        if (other.missingSchemas != null) {
            if (newMissingSchemas == null) {
                newMissingSchemas = new HashSet<>();
            }
            newMissingSchemas.addAll(Arrays.asList(other.missingSchemas));
        }

        String[] retMissingSchemas = newMissingSchemas == null ? null
                : newMissingSchemas.toArray(new String[newMissingSchemas.size()]);

        HashSet<Error> newErrors = errors == null ? null : new HashSet<>(Arrays.asList(errors));

        if (other.errors != null) {
            if (newErrors == null) {
                newErrors = new HashSet<>();
            }

            newErrors.addAll(Arrays.asList(other.errors));
        }

        Error[] retErrors = newErrors == null ? null : newErrors.toArray(new Error[newErrors.size()]);

        return new ValidationResult(retMissingSchemas, retErrors);
    }

    /**
     * @return The list of schemas referenced from the "root" schemas that weren't found.
     */
    @Nullable
    public String[] getMissingSchemas() {
        return missingSchemas;
    }

    /**
     * @return Errors found during checking
     */
    @Nullable
    public Error[] getErrors() {
        return errors;
    }

    public boolean isSuccessful() {
        return (missingSchemas == null || missingSchemas.length == 0) && (errors == null || errors.length == 0);
    }

    public ConfigurationException asException() {
        StringBuilder sb = new StringBuilder();
        if (missingSchemas != null && missingSchemas.length > 0) {
            sb.append("Found missing schemas referenced in the extension configurations: ");
            sb.append(String.join(", ", Arrays.asList(missingSchemas)));
        }

        if (errors != null && errors.length > 0) {
            if (sb.length() != 0) {
                sb.append("\n");
            }

            sb.append("The extension configuration failed to validate:\n");
            sb.append(Stream.of(errors).map(e -> e.message).collect(Collectors.joining("\n")));
        }

        return new ConfigurationException(sb.toString());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ValidationResult[");
        sb.append("errors=").append(Arrays.toString(errors));
        sb.append(", missingSchemas=").append(Arrays.toString(missingSchemas));
        sb.append(']');
        return sb.toString();
    }
}
