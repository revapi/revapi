package org.revapi.java.transforms.methods;

import java.io.Reader;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;

import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.Element;
import org.revapi.java.JavaModelElement;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class AnnotationTypeAttributeRemoved implements DifferenceTransform {
    private Locale locale;

    @Nullable
    @Override
    public String[] getConfigurationRootPaths() {
        return null;
    }

    @Nullable
    @Override
    public Reader getJSONSchema(@Nonnull String configurationRootPath) {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        locale = analysisContext.getLocale();
    }

    @Nullable
    @Override
    public Difference transform(@Nullable Element oldElement, @Nullable Element newElement,
        @Nonnull Difference difference) {

        if (Code.METHOD_REMOVED != Code.fromCode(difference.code)) {
            return difference;
        }

        @SuppressWarnings("ConstantConditions")
        ExecutableElement method = (ExecutableElement) ((JavaModelElement) oldElement).getModelElement();

        if (method.getEnclosingElement().getKind() == ElementKind.ANNOTATION_TYPE) {
            AnnotationValue defaultValue = method.getDefaultValue();

            if (defaultValue == null) {
                return Code.METHOD_ATTRIBUTE_WITHOUT_DEFAULT_REMOVED_FROM_ANNOTATION_TYPE.createDifference(locale);
            } else {
                return Code.METHOD_ATTRIBUTE_WITH_DEFAULT_REMOVED_FROM_ANNOTATION_TYPE.createDifference(locale);
            }
        }

        return difference;
    }

    @Override
    public void close() {
    }
}
