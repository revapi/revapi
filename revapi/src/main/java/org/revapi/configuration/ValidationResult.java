package org.revapi.configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;

/**
 * @author Lukas Krejci
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

    public static ValidationResult success() {
        return new ValidationResult(null, null);
    }

    private final String[] missingSchemas;
    private final Error[] errors;

    public ValidationResult(@Nullable String[] missingSchemas, @Nullable Error[] errors) {
        this.missingSchemas = missingSchemas;
        this.errors = errors;
    }

    public static ValidationResult fromTv4Results(ModelNode tv4ResultJSON) {
        if (tv4ResultJSON.get("valid").asBoolean()) {
            return ValidationResult.success();
        } else {

            List<String> missingSchemas = new ArrayList<>();

            for (ModelNode missing : tv4ResultJSON.get("missing").asList()) {
                missingSchemas.add(missing.asString());
            }

            List<Error> errors = new ArrayList<>();
            for (ModelNode error : tv4ResultJSON.get("errors").asList()) {
                int code = error.get("code").asInt();
                String message = error.get("message").asString();
                String dataPath = error.get("dataPath").asString();

                errors.add(new Error(code, message, dataPath));
            }

            String[] missingSchemasA = missingSchemas.isEmpty() ? null : new String[missingSchemas.size()];
            if (missingSchemasA != null) {
                missingSchemasA = missingSchemas.toArray(missingSchemasA);
            }

            Error[] errorsA = errors.isEmpty() ? null : new Error[errors.size()];
            if (errorsA != null) {
                errorsA = errors.toArray(errorsA);
            }

            return new ValidationResult(missingSchemasA, errorsA);
        }
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

        String[] retMissingSchemas =
            newMissingSchemas == null ? null : newMissingSchemas.toArray(new String[newMissingSchemas.size()]);

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

    @Nullable
    public String[] getMissingSchemas() {
        return missingSchemas;
    }

    @Nullable
    public Error[] getErrors() {
        return errors;
    }

    public boolean isSuccessful() {
        return missingSchemas == null && errors == null;
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
